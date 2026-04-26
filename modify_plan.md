# CreateFluid 物流系统修改方案（基于 Create 源码分析）

## 一、源码分析后的关键发现

### 1.1 原版 LogisticsManager 的分发链路

```
FactoryPanelBehaviour.tryRestock() / tickRequests()
    -> LogisticsManager.broadcastPackageRequest()
        -> LogisticsManager.findPackagersForRequest()
            -> 遍历 LogisticallyLinkedBehaviour links
                -> link.processRequest() 
                    -> PackagerLinkBlockEntity.processRequest()
                        -> getPackager()  // instanceof PackagerBlockEntity
                        -> packager.getAvailableItems()
                        -> summary.getCountOf(stack)
                        -> 返回 Pair<PackagerBlockEntity, PackagingRequest>
            -> 返回 Multimap<PackagerBlockEntity, PackagingRequest>
        -> LogisticsManager.performPackageRequests()
            -> packager.attemptToSend(queuedRequests)
```

### 1.2 当前系统其实已经"半通"

看了源码才发现，**原版分发链路对 CanFiller 的兼容性比想象中好**：

1. `PackagerLinkBlockEntity.getPackager()` 用的是 `instanceof PackagerBlockEntity`，而 `CanFillerBlockEntity extends PackagerBlockEntity`，所以 **Stock Link 能正确找到 CanFiller**。
2. `PackagerLinkBlockEntity.processRequest()` 调用 `packager.getAvailableItems()`。CanFiller 重写了这个方法，在 `super.getAvailableItems()` 的基础上混入了 `FluidManifestItem`。
3. `summary.getCountOf(stack)` 对 `FluidManifestItem` 能匹配（因为 `InventorySummaryMixin` 按 `fluidId` 做了兼容）。
4. `performPackageRequests()` 调用 `CanFiller.attemptToSend(queuedRequests)`。CanFiller 重写后，如果是 `FluidManifestItem` 就走流体打包逻辑。

**结论**：在 `restocker` 和 `非 restocker` 两种模式下，LogisticsManager 都能把 FluidManifestItem 的请求正确路由到 CanFiller。

### 1.3 真正的问题不是"分发不到"，而是以下细节缺陷

| 缺陷 | 影响 | 严重程度 |
|------|------|----------|
| CanFiller 红石触发时先走 `super.attemptToSend(null)` | 会尝试从物品库存打包，再打包流体，职责混乱 | 高 |
| CopperCanItem 包裹被送到普通 Packager 时解包异常 | 普通 Packager 的 `unwrapBox` 把 CopperCanItem 当空包裹处理（直接 return true），铜罐被"吞" | 高 |
| `FluidRequestKey` 只比较 `ResourceLocation` | 不支持 Create 的 Fluid Components（染色流体、药水等） | 中 |
| 解包没有 Basin 预模拟 | Basin 的 Input Tank 可能装不下，导致填充失败或部分丢失 | 中 |
| 工厂面板只能按 Bucket 步进 | 无法精确设置 500mB、250mB 等数值 | 中 |
| 一包裹一罐 | 运输效率低，大量流体时包裹数过多 | 低 |

---

## 二、修改方案

### 修改 1：CanFiller 红石触发逻辑修正（高优先级）

**问题代码**（`CanFillerBlockEntity.java`）：
```java
@Override
public void attemptToSend(List<PackagingRequest> queuedRequests) {
    if (queuedRequests == null) {
        super.attemptToSend(null);  // ❌ 先打包物品！
        if (heldBox.isEmpty() && animationTicks == 0) {
            attemptToSendFluidPassive();
        }
        return;
    }
    // ...
}
```

**修改方案**：

方案 A（推荐）：红石触发时直接走流体被动打包，不再调用 `super.attemptToSend(null)`。

```java
@Override
public void attemptToSend(List<PackagingRequest> queuedRequests) {
    if (queuedRequests == null) {
        // CanFiller 在红石触发时只处理流体被动打包
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;
        attemptToSendFluidPassive();
        return;
    }

    if (queuedRequests.isEmpty())
        return;

    PackagingRequest nextRequest = queuedRequests.get(0);
    if (!(nextRequest.item().getItem() instanceof FluidManifestItem)) {
        // 只有非流体请求时才调用父类处理
        super.attemptToSend(queuedRequests);
        return;
    }

    attemptToSendFluid(queuedRequests);
}
```

**说明**：
- CanFiller 的设计定位是"流体灌装/流体物流接口"，不是通用打包机。
- 如果用户确实需要在同一个方块上同时打包物品和流体，应该使用原版 Packager + 独立的 FluidPackager（见修改 6）。
- 这个修改避免了 CanFiller 从背面的物品库存中意外抽取物品。

---

### 修改 2：防止 CopperCanItem 被普通 Packager 错误处理（高优先级）

**问题分析**：

普通 Packager 的 `unwrapBox()` 逻辑：
```java
ItemStackHandler contents = PackageItem.getContents(box);  // 对 CopperCanItem 返回空 Handler
List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents); // 空列表
if (items.isEmpty())
    return true;  // ❌ 直接返回 true，调用方认为"解包成功"
```

如果 CopperCanItem 被错误送到普通 Packager（例如地址匹配错误、Frogport 投递错误），它会直接被当作"空包裹"消耗掉，流体丢失。

**修改方案**：

给 `CopperCanItem` 添加一个显式的标记，让普通 Packager 在 `unwrapBox` 时识别出"这是流体包裹，我不能处理"，然后返回 `false`，让包裹保留或弹出。

**具体实现**：通过 Mixin 修改 `PackagerBlockEntity.unwrapBox()`

```java
@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin {
    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void fluid$rejectFluidPackage(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (CopperCanItem.isCopperCan(box)) {
            // 普通 Packager 不能解包铜罐，返回 false 让包裹保留
            cir.setReturnValue(false);
        }
    }
}
```

**副作用**：如果 CopperCanItem 被送到普通 Packager，它会留在 Packager 上不被处理。这比"被吞掉"好得多——用户可以肉眼看到问题并调整地址。

更进一步：可以让普通 Packager 在检测到铜罐时，尝试把它重新放入输出队列（`queuedExitingPackages`）或者直接弹出到地上。但返回 `false` 是最安全的最小改动。

---

### 修改 3：`FluidRequestKey` 支持 Fluid Components（中优先级）

**问题**：当前 `FluidRequestKey` 只比较 `ResourceLocation`：
```java
public record FluidRequestKey(ResourceLocation fluidId) {
    public boolean matches(FluidStack fluid) {
        return fluidId.equals(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
    }
}
```

Create 的流体系统已经支持 Data Components（如染色、温度、药水效果等）。两个同种流体但 Components 不同的 `FluidStack` 会被当作同一种，导致错误匹配。

**修改方案**：

```java
public record FluidRequestKey(ResourceLocation fluidId, @Nullable DataComponentPatch components) {
    
    public static FluidRequestKey of(FluidStack fluid) {
        return new FluidRequestKey(
            BuiltInRegistries.FLUID.getKey(fluid.getFluid()),
            fluid.getComponentsPatch()
        );
    }

    public boolean matches(FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        if (!fluidId.equals(BuiltInRegistries.FLUID.getKey(fluid.getFluid()))) return false;
        return Objects.equals(components, fluid.getComponentsPatch());
    }
}
```

**联动修改**：
1. `FluidManifestItem.of()` 创建 key 时需要传入 components。
2. `FluidManifestContent` 的 codec 需要增加 `components` 字段。
3. `InventorySummaryMixin` 中的匹配逻辑需要从比较 `fluidId` 改为比较完整的 `FluidRequestKey`。

---

### 修改 4：Basin 解包目标适配器（中优先级）

**问题**：Create 的 `BasinBlockEntity` 有 `SmartFluidTankBehaviour.INPUT` 和 `.OUTPUT` 两个独立 tank。原版 `DefaultUnpackingHandler` 只处理物品插入，对流体包裹的解包没有预模拟逻辑。

当 CanFiller 解包铜罐时，直接调用 `fluidHandler.fill()`。如果目标 Basin 的 Input Tank 已经有其他流体或容量不足，可能只部分填充，剩余流体丢失（因为铜罐已经被消耗）。

**修改方案**：

**步骤 1**：在 CanFiller 的 `unwrapCopperCan` 中，添加预模拟逻辑。

```java
private boolean unwrapCopperCan(ItemStack box, boolean simulate) {
    if (animationTicks > 0)
        return false;

    IFluidHandler fluidHandler = getFluidHandler();
    if (fluidHandler == null)
        return false;

    FluidStack fluid = CopperCanItem.getFluid(box);
    if (fluid.isEmpty())
        return true;

    // 获取目标 BlockEntity，用于特殊容器判断
    Direction facing = getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
    BlockPos targetPos = worldPosition.relative(facing.getOpposite());
    BlockEntity targetBE = level.getBlockEntity(targetPos);

    // 预模拟填充
    int simulated = fluidHandler.fill(fluid, IFluidHandler.FluidAction.SIMULATE);
    if (simulated < fluid.getAmount()) {
        // 如果目标 Basin 的 Input Tank 装不下，尝试精确预模拟
        if (targetBE instanceof BasinBlockEntity basin) {
            SmartFluidTankBehaviour inputTank = basin.getBehaviour(SmartFluidTankBehaviour.INPUT);
            if (inputTank != null) {
                IFluidHandler inputHandler = inputTank.getCapability();
                if (inputHandler != null) {
                    // 对 Basin 的每个 tank 做精确预模拟
                    int totalCanFill = 0;
                    FluidStack remaining = fluid.copy();
                    for (int tank = 0; tank < inputHandler.getTanks() && !remaining.isEmpty(); tank++) {
                        FluidStack inTank = inputHandler.getFluidInTank(tank);
                        if (inTank.isEmpty() || FluidStack.isSameFluidSameComponents(inTank, remaining)) {
                            int space = inputHandler.getTankCapacity(tank) - inTank.getAmount();
                            int toFill = Math.min(remaining.getAmount(), space);
                            if (toFill > 0 && inputHandler.isFluidValid(tank, remaining)) {
                                totalCanFill += toFill;
                                remaining.shrink(toFill);
                            }
                        }
                    }
                    if (totalCanFill < fluid.getAmount()) {
                        return false; // 装不下，不解包
                    }
                }
            }
        } else {
            return false; // 普通容器装不下，不解包
        }
    }

    if (simulate)
        return true;

    fluidHandler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    previouslyUnwrapped = box.copyWithCount(1);
    animationInward = true;
    animationTicks = CYCLE;
    notifyUpdate();
    return true;
}
```

**步骤 2**：如果未来需要支持更多特殊容器（如其他模组的 Multiblock Tank），可以把预模拟逻辑抽象为 `FluidPackageTargetAdapter` 接口，类似 CreateFluidLogistic 的设计。但当前阶段，直接在 `unwrapCopperCan` 中处理 Basin 是最小改动。

---

### 修改 5：工厂面板支持 mB / Bucket 双模式（中优先级）

**问题**：当前 `FactoryPanelScreenMixin` 和 `FactoryPanelBehaviourMixin` 把面板强制改为 Bucket 模式，最小步进 1000mB。

**修改方案**：参考 CreateFluidLogistic 的实现，支持 Row 0 = mB 模式（步进 10/100/1000），Row 1 = Bucket 模式（步进 1/10/100）。

**关键修改点**：

1. `FactoryPanelBehaviourMixin.createBoard()`：
```java
@Inject(method = "createBoard", at = @At("HEAD"), cancellable = true)
private void fluid$createFluidBoard(Player player, BlockHitResult hitResult, CallbackInfoReturnable<ValueSettingsBoard> cir) {
    FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
    if (!fluid$hasFluidFilter(behaviour)) return;
    
    ValueSettingsBoard fluidBoard = new ValueSettingsBoard(
        CreateLang.translate("factory_panel.target_amount").component(),
        100, 10,
        List.of(
            CreateLang.text("mB").component(),   // Row 0: mB mode, step = 10mB
            CreateLang.text("B").component()     // Row 1: Bucket mode, step = 1B
        ),
        new ValueSettingsFormatter(this::formatValue)
    );
    cir.setReturnValue(fluidBoard);
}
```

2. `FactoryPanelBehaviourMixin.setValueSettings()`：
```java
@Inject(method = "setValueSettings", at = @At("HEAD"), cancellable = true)
private void fluid$setFluidValueSettings(Player player, ValueSettings settings, boolean ctrlDown, CallbackInfo ci) {
    FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
    if (!fluid$hasFluidFilter(behaviour)) return;
    
    int amountMb;
    if (settings.row() == 1) {
        // Bucket mode: value = buckets
        amountMb = settings.value() * 1000;
    } else {
        // mB mode: value * 10 = mB
        amountMb = settings.value() * 10;
    }
    
    behaviour.count = amountMb;
    behaviour.upTo = true;
    behaviour.panelBE().redraw = true;
    behaviour.blockEntity.setChanged();
    behaviour.blockEntity.sendData();
    playFeedbackSound(behaviour);
    resetTimerSlightly();
    ci.cancel();
}
```

3. `FactoryPanelBehaviourMixin.getValueSettings()`：
```java
@Inject(method = "getValueSettings", at = @At("HEAD"), cancellable = true)
private void fluid$getFluidValueSettings(CallbackInfoReturnable<ValueSettings> cir) {
    FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
    if (!fluid$hasFluidFilter(behaviour)) return;
    
    int count = behaviour.count;
    boolean useBuckets = count >= 1000 && count % 1000 == 0;
    int displayValue = useBuckets ? count / 1000 : count / 10;
    int row = useBuckets ? 1 : 0;
    cir.setReturnValue(new ValueSettings(row, displayValue));
}
```

4. `FactoryPanelScreenMixin.mouseScrolled()`：
```java
// 修改滚动步进：在 mB 模式下滚动一次 +/- 1000mB（或 100mB with shift）
// 在 Bucket 模式下滚动一次 +/- 1 Bucket（或 10 with shift）
```

---

### 修改 6：一包裹多罐（低优先级）

**问题**：当前 `CopperCanItem.create()` 创建的包裹只装一罐。

**修改方案**：让 `CanFiller` 在创建包裹时，尽量把多个铜罐塞入一个 `PackageItem` 中（利用 `PackageItem.SLOTS = 9`）。

```java
private void attemptToSendFluid(List<PackagingRequest> queuedRequests) {
    PackagingRequest nextRequest = queuedRequests.get(0);
    FluidRequestKey requestedKey = FluidManifestItem.readKey(nextRequest.item());
    if (requestedKey == null) {
        queuedRequests.remove(0);
        return;
    }

    int requestedMb = nextRequest.getCount();
    int packageCapacity = CFCommonConfig.getFluidPerPackage();
    int maxTanksPerPackage = PackageItem.SLOTS; // 9
    
    // 计算这一个包裹能装多少罐
    int maxMbPerPackage = packageCapacity * maxTanksPerPackage;
    int toExtractMb = Math.min(requestedMb, maxMbPerPackage);
    
    FluidStack extracted = executePlan(requestedKey, toExtractMb);
    if (extracted.isEmpty()) {
        queuedRequests.remove(0);
        return;
    }

    // 把提取的流体拆分成多个铜罐，装入 PackageItem
    ItemStackHandler packageContents = new ItemStackHandler(PackageItem.SLOTS);
    FluidStack remaining = extracted.copy();
    while (!remaining.isEmpty()) {
        int tankAmount = Math.min(remaining.getAmount(), packageCapacity);
        FluidStack tankFluid = remaining.copyWithAmount(tankAmount);
        ItemStack can = CopperCanItem.create(tankFluid, packageCapacity);
        ItemHandlerHelper.insertItemStacked(packageContents, can, false);
        remaining.shrink(tankAmount);
    }

    ItemStack fluidPackage = PackageItem.containing(packageContents);
    
    // ... 后续设置地址、订单信息等
}
```

**注意**：这要求 `CopperCanItem` 能被正确序列化/反序列化到 `ItemStackHandler` 中。如果 `CopperCanItem` 使用了 DataComponents 存储流体信息，这应该没问题。

解包时也需要相应修改：从 `PackageItem.getContents(box)` 中取出所有 `CopperCanItem`，逐个倒出流体。

---

### 修改 7：引入专门的 `FluidPackagerBlockEntity`（可选，长期方向）

如果未来想把"流体物流"和"灌装"完全解耦，可以新增一个 `FluidPackagerBlockEntity`：

```java
public class FluidPackagerBlockEntity extends SmartBlockEntity {
    private TankManipulationBehaviour fluidTarget;
    // ... 类似原版 Packager，但只处理流体
}
```

**但这需要修改原版 LogisticsManager 的分发逻辑**，因为：
1. `PackagerLinkBlockEntity.getPackager()` 只认 `PackagerBlockEntity`。
2. `LogisticsManager.findPackagersForRequest()` 返回的是 `Multimap<PackagerBlockEntity, PackagingRequest>`。
3. `performPackageRequests()` 遍历的是 `PackagerBlockEntity`。

如果要支持非 `PackagerBlockEntity` 的流体打包机，需要：
- 新增 `IFluidPackager` 接口（已经有了）。
- Mixin `LogisticsManager.findPackagersForRequest()`：在遍历 links 时，不仅查找 `PackagerBlockEntity`，还要查找实现了 `IFluidPackager` 的方块。
- Mixin `LogisticsManager.performPackageRequests()`：增加对 `IFluidPackager` 的处理分支。

**这个改动的 Mixin 侵入深度与 CreateFluidLogistic 相当。考虑到当前系统已经能工作，建议暂时不引入独立 FluidPackager，优先把上述 6 个修改做好。**

---

## 三、修改优先级与工作量评估

| 修改 | 优先级 | 预估工作量 | 文件变动 |
|------|--------|-----------|----------|
| 1. CanFiller 红石触发逻辑 | 高 | 小（1 个方法） | `CanFillerBlockEntity.java` |
| 2. 普通 Packager 拒绝 CopperCanItem | 高 | 小（新增 Mixin） | 新增 `PackagerBlockEntityMixin.java` |
| 3. FluidRequestKey 支持 Components | 中 | 中（多处联动） | `FluidRequestKey.java`, `FluidManifestContent.java`, `InventorySummaryMixin.java` |
| 4. Basin 解包预模拟 | 中 | 中 | `CanFillerBlockEntity.java` |
| 5. 工厂面板双模式 | 中 | 中 | `FactoryPanelBehaviourMixin.java`, `FactoryPanelScreenMixin.java` |
| 6. 一包裹多罐 | 低 | 中 | `CanFillerBlockEntity.java`, `CopperCanItem.java` |
| 7. 独立 FluidPackager | 可选 | 大 | 新增多个文件 + 深度 Mixin |

---

## 四、测试建议

每做完一个修改后，按以下场景验证：

1. **Restocker 模式**：FactoryGauge + CanFiller + Stock Link，设置流体目标量为 5000mB，观察是否能正确请求并收到铜罐包裹。
2. **非 Restocker 模式**：FactoryPanel 配方需要 FluidManifestItem(水) + 铁锭，观察 LogisticsManager 是否分别路由到 CanFiller 和普通 Packager。
3. **红石触发**：给 CanFiller 红石信号，确认它只打包流体、不碰物品。
4. **Basin 解包**：CanFiller 解包到 Basin，Basin Input Tank 已有其他流体时，应拒绝解包。
5. **错误投递**：把 CopperCanItem 送到普通 Packager，确认不被吞掉（return false）。
6. **面板精度**：工厂面板设置 2500mB、3750mB 等数值，确认请求量正确。
