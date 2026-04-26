# CreateFluid 物流系统对比分析报告

## 一、三者架构总览

### 1. CreateFluid（当前项目）
- **核心思路**：在原版 PackagerBlockEntity 基础上，通过 `CanFillerBlockEntity` 继承打包机并混入流体处理能力；用 `FluidManifestItem` 作为"虚拟流体标识物"混入原版物流网络。
- **关键组件**：
  - `FluidManifestItem`：通过 DataComponent 存储 `fluidId` 和 `amount`，作为工厂面板和库存总结中的流体代理。
  - `CanFillerBlockEntity extends PackagerBlockEntity implements IFluidLogisticsPackager`：既是灌装机，也是流体打包机。
  - `FluidInterface` / `SmartFluidInterface`：提供流体容器与物流网络的接口，支持过滤。
  - Mixin 修改了 `FactoryPanelBehaviour`、`FactoryPanelScreen`、`InventorySummary`、`StockKeeperRequestScreen` 等。

### 2. CreateFluidLogistic（参考项目1）
- **核心思路**：不修改原版 Packager，而是新增独立的 `FluidPackagerBlockEntity`；通过深度 Mixin `LogisticsManager` 在物流调度层面实现"混合订单"（物品+流体统一分发）。
- **关键组件**：
  - `FluidPackagerBlockEntity`：独立方块，专责流体打包/解包，不继承原版 Packager。
  - `IFluidPackager`：定义流体打包机的完整接口（库存扫描、请求处理、地址管理、忙碌状态）。
  - `CompressedTankItem`：分 Virtual（用于网络中标识流体）和 Real（实际装在包裹中）两种模式。
  - `FluidPackageItem extends PackageItem`：专门的流体包裹外观。
  - `FluidPackageTargetAdapter`：解包目标适配器（对 Basin 等多罐容器做特殊容量模拟）。
  - `FluidTransporter`：直接传输流体的设备，不经过包裹化。
  - 深度 Mixin `LogisticsManager.broadcastPackageRequest` / `findPackagersForRequest`：在订单分发层就区分流体/物品，统一调度。

### 3. Repackaged（参考项目2）
- **核心思路**：基于外部库 `Deployer Lib` 的泛型抽象框架，将物品/流体/能量统一为 `StockInventoryType<K, V, H>` 范式。
- **关键组件**：
  - `AbstractPackagerBlockEntity<Fluid, FluidStack, IFluidHandler>`：泛型基类，理论上可同时支持物品、流体、能量。
  - `FluidStockInventoryType`：实现值处理、存储处理、网络处理、包裹处理四大接口。
  - `FluidPanelBehaviour extends StockPanelBehaviour`：工厂面板上的流体面板行为。
  - 流体包裹为"瓶子"（Bottle），每个固定 1000mB。
  - `PackageShelfBlockEntity extends RepackagerBlockEntity`：可堆叠的包裹架，增大重新打包缓存。
  - `PackagerConnectorBlockEntity`：打包机连接器，允许多个打包机串联传递包裹。

---

## 二、当前项目 vs CreateFluidLogistic

| 维度 | CreateFluid | CreateFluidLogistic |
|------|-------------|---------------------|
| **打包机设计** | `CanFillerBlockEntity` 继承原版 Packager，职责混杂（灌装+打包） | 独立的 `FluidPackagerBlockEntity`，职责单一清晰 |
| **物流调度层** | 仅在 `CanFiller.attemptToSend()` 中拦截流体请求，**未修改 LogisticsManager** | 深度 Mixin `LogisticsManager`，在 `broadcastPackageRequest` / `findPackagersForRequest` 层统一处理混合订单 |
| **混合订单** | 流体和物品订单由原版 LogisticsManager 分别处理，协调逻辑薄弱，容易出错 | 同一次订单遍历中，自动区分流体/物品，分别路由到 `IFluidPackager` 或 `PackagerBlockEntity` |
| **虚拟标识物** | `FluidManifestItem`（简洁，只存 fluidId+amount） | `CompressedTankItem`（分 Virtual/Real 模式，逻辑较重但支持 Components 匹配） |
| **包裹容量** | `CopperCanItem` 单罐，容量由配置决定，**一包裹一罐** | `FluidPackageItem` 内部用 `ItemStackHandler`（9 槽），**一包裹多罐**，总容量 = 单罐容量 × 槽位数 |
| **解包适配** | 无特殊适配，直接 `fluidHandler.fill()` | `FluidPackageTargetAdapter` 体系，对 Basin 做多罐容量预模拟，避免解包失败 |
| **直接传输** | 无 | `FluidTransporter` 支持短距离直接抽送流体，无需包裹化 |
| **工厂面板精度** | 仅支持 Bucket（1000mB）步进 | 支持 **mB / B 双模式** 调节，最小 10mB |
| **库存去重** | 无 | `collectAndDeduplicateLinks`：同一库存的多 Link 随机去重，避免重复统计 |
| **CC: Tweaked** | 无 | 完整的 ComputerCraft 事件支持（package_created / package_received） |
| **目标容器范围** | FluidInterface / SmartFluidInterface 只能贴在方块侧面 | SmartFaucet + FluidTransporter 覆盖更多场景（如精确滴灌、无限水源抽取） |

---

## 三、当前项目 vs Repackaged

| 维度 | CreateFluid | Repackaged |
|------|-------------|------------|
| **架构抽象层** | 针对流体特化，无泛型抽象 | `StockInventoryType<K,V,H>` 泛型框架，可复用于能量/流体/物品 |
| **流体包裹形式** | 铜罐（Can） | 瓶子（Bottle），视觉效果独特 |
| **包裹容量** | 可配置（通常 >1000mB） | **固定 1000mB/瓶**，且每包裹只有 1 瓶（`getMaxPackageSlots=1`） |
| **重新打包缓存** | 原版 Repackager 默认缓存 | `PackageShelfBlockEntity`：**可垂直堆叠扩展缓存容量** |
| **打包机串联** | 无 | `PackagerConnectorBlockEntity`：相邻打包机之间可传递包裹 |
| **解包扩展** | 无 | `VanillaCrafterUnpackingHandler`：对原版合成器做配方感知的精确解包 |
| **工厂面板渲染** | 用 ItemStack 渲染 + 重定向 | `FluidPanelBehaviour` 直接调用 `FluidRenderer.renderFluidBox` 渲染流体模型 |
| **依赖关系** | 仅依赖 Create + NeoForge | 强依赖 `Deployer Lib`（外部未开源/未包含库） |

---

## 四、当前项目（CreateFluid）的具体缺点

1. **物流调度层未打通（最大缺陷）**
   - 当前仅在 `CanFillerBlockEntity.attemptToSend()` 中做流体请求的被动拦截。当 LogisticsManager  broadcast 一个混合订单时，原版逻辑只会把 `FluidManifestItem` 当作普通物品发给普通 Packager，而不会智能路由到 `CanFiller`。这导致：
     - 混合订单（同时需要铁锭+水）时，物品部分和流体部分可能分发到不同打包机，甚至发错。
     - `CanFiller` 如果被当作普通 Packager 接到非流体请求，行为不可预期。

2. **CanFiller 职责混杂**
   - `CanFillerBlockEntity` 既是灌装设备（用于机械动力产线），又是物流打包机。两种逻辑耦合在一起，导致：
     - NBT 序列化、动画状态、红石触发逻辑混在一起。
     - 维护成本高，后续增加功能（如 CC 集成）需要继续往这个类里堆代码。

3. **InventorySummary 匹配粒度粗**
   - `InventorySummaryMixin` 只按 `fluidId`（ResourceLocation）匹配，没有考虑 `FluidStack` 的 `ComponentsPatch`（例如 Create 的新版染色流体、药水效果等）。
   - 虽然当前大部分流体没有复杂 Component，但这是潜在 Bug。

4. **包裹容量受限**
   - `CopperCanItem` 一包裹只能装一罐。若配置为 4000mB/罐，大量流体运输时包裹数量过多，增加物流网络负担。

5. **解包鲁棒性不足**
   - 解包时直接调用 `fluidHandler.fill()`，没有预模拟。对于 `BasinBlockEntity` 这种内部有 Input/Output 多罐分离的容器，可能出现"部分填充后剩余流体无法进入"的僵局。

6. **工厂面板操作粒度粗**
   - `FactoryPanelScreenMixin` 中，滚动和点击的步进只有 1000mB（1 Bucket）和 10000mB。无法设置 500mB、250mB 等精细数值。

7. **缺少直接流体传输手段**
   - 不是所有场景都需要包裹化。短距离、高频、小量的流体抽送（如从蓄水池到锅炉）用包裹反而低效。

8. **缺少便携终端**
   - 玩家必须站在 StockKeeper/FactoryPanel 前才能查看/请求流体库存。没有手持设备远程操作。

9. **Frogport/Mailbox 配置工具过重**
   - 当前用 Baton（指挥棒）做 Frogport 和 Mailbox 的配置，需要专门的交互处理器、选择处理器、网络包。CreateFluidLogistic 的 HandPointer 也是类似思路，但两者都增加了额外的物品和 UI 复杂度。

10. **无 ComputerCraft 集成**
    - 现代自动化整合包几乎都会装 CC，缺少外围设备支持是一个明显短板。

---

## 五、改进建议（按优先级排序）

### 高优先级

1. **Mixin LogisticsManager，实现混合订单统一调度**
   - 参照 CreateFluidLogistic 的 `LogisticsManagerMixin`，在 `broadcastPackageRequest` 和 `findPackagersForRequest` 的 HEAD 注入。
   - 遍历订单时检查 `FluidManifestItem`，将流体请求路由到实现了 `IFluidLogisticsPackager` 的方块（如 `CanFiller` 或未来专门的流体打包机），物品请求仍走原版 Packager。
   - 这是解决"发错包裹"和"混合订单不协调"的根本办法。

2. **引入解包目标适配器（Target Adapter）**
   - 创建 `FluidPackageTargetAdapter` 接口。
   - 至少实现一个 `BasinFluidPackageTargetAdapter`，对 Basin 的 `SmartFluidTankBehaviour.INPUT` 做预模拟填充，确保所有流体都能被接收后再执行真正解包。

3. **改进工厂面板的数值精度**
   - 将 `FactoryPanelScreenMixin` 和 `FactoryPanelBehaviourMixin` 中的步进逻辑改为双模式：
     - Row 0：mB 模式（步进 10/100/1000 mB）
     - Row 1：B 模式（步进 1/10/100 Bucket）
   - 参照 CreateFluidLogistic 的 `FluidGaugeHelper` 和 `FluidAmountHelper`。

4. **增加包裹多槽容量**
   - 修改 `CopperCanItem` 的包裹创建逻辑，允许一个包裹内放置多个铜罐（例如最多 4 罐或 9 罐），提升运输效率。

### 中优先级

5. **抽离流体打包机接口**
   - 即使继续用 `CanFiller` 充当流体打包机，也应该把物流相关的代码（`attemptToSendFluid`、`unwrapCopperCan`、`getFluidSummary` 等）抽到一个独立的 `FluidPackagerBehaviour` 或接口默认方法中，降低耦合。

6. **支持 FluidStack Components 匹配**
   - 将 `FluidRequestKey` 从仅比较 `ResourceLocation` 改为同时比较 `Fluid` + `ComponentsPatch`（使用 `FluidStack.isSameFluidSameComponents`）。
   - `InventorySummaryMixin` 中的匹配逻辑也要同步更新。

7. **添加直接流体传输方块**
   - 实现类似 `FluidTransporter` 的方块：单方块，带过滤，直接从背面抽取流体并向正面输出，无需包裹化。适合短距离高频场景。

8. **ComputerCraft 集成**
   - 为 `CanFillerBlockEntity` 或未来的流体打包机添加 `AbstractComputerBehaviour`。
   - 暴露事件：`package_created`、`package_received`、`stock_changed`。
   - 暴露方法：`getAvailableFluids()`、`setAddress(string)`、`activate()`。

### 低优先级 / 差异化方向

9. **便携式流体库存终端**
   - 参照 CreateFluidLogistic 的 `PortableStockTickerItem`，做一个手持设备，可以查看物流网络中的流体库存并下订单。

10. **PackageShelf / PackagerConnector（学习 Repackaged）**
    - 如果重新打包（Repackager）成为瓶颈，可以引入可堆叠的 `PackageShelf` 增加缓存。
    - `PackagerConnector` 可以让多个打包机共享同一个目标库存的出/入口，提升吞吐。

---

## 六、参考项目的缺点 & 我们可以做得更好的地方

### CreateFluidLogistic 的不足

1. **过度依赖深度 Mixin，兼容性脆弱**
   - 大量修改 `LogisticsManager`、`PackagerBlockEntity`、`FactoryPanelBehaviour` 的内部逻辑。Create 每次小版本更新都可能导致 Mixin 映射失效。
   - **我们的优势**：当前项目的 Mixin 数量更少、侵入更浅，维护成本更低。改进时应该继续保持"最小必要 Mixin"原则，优先用 Create 的 API（如 `IdentifiedInventory`、`PackagingRequest` 的公共方法）而不是直接注入私有逻辑。

2. **CompressedTankItem 的 Virtual/Real 双模式过于复杂**
   - `InventorySummaryMixin` 中需要同时处理虚拟储罐（网络中代表流体）和真实储罐（装在包裹里），匹配逻辑分支多、容易出错。
   - **我们的优势**：`FluidManifestItem` 设计更简洁，始终只是一个"代理标识物"，不区分模式。应该继续保持这种简洁性，不要引入 Virtual/Real 的概念。

3. **FactoryPanel 对 CAL（Create Addon Logistics）的 hacky 兼容**
   - 用反射去清除 CAL 的 `promiseLimit`、`additionalStock` 等字段，非常脆弱。
   - **我们的优势**：当前没有这种外部模组的特殊兼容，代码更干净。如果需要兼容其他模组，应该通过 API 接口而不是反射。

4. **缺少能量物流**
   - CreateFluidLogistic 只做了流体，架构没有为后续扩展预留空间。
   - **我们的优势**：如果未来想扩展，可以借鉴 Repackaged 的泛型思路，提前设计 `StockInventoryType` 风格的抽象，但不必像 Repackaged 那样重度依赖外部库。

### Repackaged 的不足

1. **强依赖 Deployer Lib，不可独立使用**
   - 核心逻辑（`AbstractPackagerBlockEntity`、`StockInventoryType`、`GenericPackageItem`）全部在外部库中，无法单独阅读或移植。
   - **我们的优势**：当前项目完全自包含，不依赖私有库，社区开发者可以直接阅读全部源码并贡献。

2. **流体包裹容量过于死板**
   - 每瓶固定 1000mB，且每包裹只能装 1 瓶（`getMaxPackageSlots=1`）。对于大流量场景（如岩浆、蒸汽），包裹吞吐量严重不足。
   - **我们的优势**：`CopperCanItem` 的容量是可配置的，而且可以通过多槽包裹进一步提升。这是我们可以明确做得更好的地方。

3. **泛型过度设计，理解门槛高**
   - `AbstractPackagerBlockEntity<Fluid, FluidStack, IFluidHandler>` 三层泛型加四个内部 Handler 接口，对普通模组开发者极不友好。
   - **我们的优势**：代码更直观、更符合 Create 的原生风格，更容易被社区接受和二次开发。

4. **缺少流体过滤和精确输出**
   - Repackaged 的流体物流主要是"批量运输"，没有 SmartFaucet 这种精确控制输出量/速率的设备。
   - **我们的优势**：当前已经有 `SmartFluidInterface` 支持过滤，可以在此基础上继续扩展精确输出控制。

---

## 七、总结

- **CreateFluidLogistic** 在"物流系统深度集成"和"功能完整性"上是标杆，但代价是高维护成本和复杂 Mixin。
- **Repackaged** 在"架构抽象"和"可扩展性"上有独到之处，但强依赖外部库且流体容量设计偏保守。
- **当前 CreateFluid** 的优势是**简洁、自包含、与 Create 原生风格一致**；最大短板是**未在 LogisticsManager 层打通混合订单调度**和**解包鲁棒性不足**。

**最紧迫的改进方向**：
1. Mixin `LogisticsManager` 实现混合订单路由。
2. 添加 Basin 等多罐容器的解包适配器。
3. 提升工厂面板精度到 mB 级。
4. 支持多罐/大容量包裹。

这四项改进完成后，CreateFluid 的流体物流系统在功能上将基本追平 CreateFluidLogistic，同时保持更低的维护成本和更好的可扩展性。
