# Smart Package System

This document records the current prototype architecture for the mixed logistics system built around:

- `Smart Re-Packager`
- `Brass Box`
- `Smart Unpackager`

The goal of this system is to make item-only, fluid-only, and mixed item+fluid logistics participate in a single repack/unpack flow, while preserving routing hints derived from Factory Gauge layout.

## 1. Design Goals

The system is meant to solve three problems:

1. Pure fluid package orders cannot be repackaged correctly by vanilla Create.
2. Mixed item and fluid orders need a single composite carrier.
3. Unpacking must respect relative vertical intent from Factory Gauge layout, but still fall back gracefully when the ideal output path is unavailable.

## 2. Core Blocks and Item

### `Brass Box`

`Brass Box` is the composite package item.

It is still treated as a package, but it can carry:

- up to `9` item slots, using Create package contents
- up to `4` fluid entries
- each fluid entry can hold up to `4000 mB`
- routing hints for item kinds and fluid kinds

Important behavior:

- it may contain only items
- it may contain only fluids
- it may contain both

Relevant implementation:

- `src/main/java/com/adonis/fluid/item/BrassBoxItem.java`
- `src/main/java/com/adonis/fluid/datacomponent/BrassBoxFluidContent.java`
- `src/main/java/com/adonis/fluid/datacomponent/BrassBoxRoutingData.java`

### `Smart Re-Packager`

`Smart Re-Packager` is a repackager variant that can consume:

- normal Create cardboard packages
- `Copper Can`
- `Brass Box`

It scans the inventory behind it, groups package fragments by order, and when an order is complete it outputs one or more `Brass Box` items.

It supports:

- pure item orders
- pure fluid orders
- mixed item+fluid orders

Relevant implementation:

- `src/main/java/com/adonis/fluid/block/SmartRepackager/SmartRepackagerBlock.java`
- `src/main/java/com/adonis/fluid/block/SmartRepackager/SmartRepackagerBlockEntity.java`

### `Smart Unpackager`

`Smart Unpackager` is an independent unpacking block. It does not inherit Create packager output semantics; it only accepts `Brass Box` and unpacks it according to routing rules.

Current prototype behavior:

- side output is chosen by block facing
- placement auto-selects a default side target by scanning `NORTH -> EAST -> SOUTH -> WEST`
- upper output can be either:
  - the block directly above
  - a flex-linked target configured by the baton

Relevant implementation:

- `src/main/java/com/adonis/fluid/block/SmartUnpackager/SmartUnpackagerBlock.java`
- `src/main/java/com/adonis/fluid/block/SmartUnpackager/SmartUnpackagerBlockEntity.java`
- `src/main/java/com/adonis/fluid/handler/SmartUnpackagerLinkHandler.java`

## 3. Order Routing Semantics

The system uses three routing states:

- `UP`
- `DOWN`
- `NONE`

These do not represent hard constraints. They represent preference.

### Rule Source

The routing hints are derived from Factory Gauge input layout:

1. Collect all source gauges feeding the requested output.
2. Compute a vertical score for each source:
   - `score = blockY * 2 + slot.yOffset`
3. If all scores are equal:
   - all involved kinds are marked `NONE`
4. Otherwise:
   - kinds present at the highest score are marked `UP`
   - all others are marked `DOWN`

For duplicated kinds across multiple source gauges:

- if a kind appears anywhere at the highest level, the entire kind is marked `UP`

This matches the intended rule of "classify by kind, not by partial quantity".

Relevant implementation:

- `src/main/java/com/adonis/fluid/mixin/FactoryPanelBehaviourMixin.java`
- `src/main/java/com/adonis/fluid/logistics/manager/MixedOrderRoutingManager.java`

## 4. Why Routing Must Be Attached At Order Creation Time

The Factory Gauge still knows exactly which input connection came from which gauge position.

However, after Create builds the actual logistics order, that source-position information is flattened into:

- `BigItemStack`
- package order context
- package fragments

That means the Smart Re-Packager cannot recover vertical source layout by inspecting package fragments alone.

Because of that, the routing hints must be computed when the Factory Gauge emits the order, then attached to the order chain and finally written onto package items.

## 5. Order Data Flow

The current mixed logistics flow is:

1. Factory Gauge computes routing hints.
2. Routing hints are attached to the order object.
3. Item packages and fluid packages are created.
4. Routing hints are copied onto those package items.
5. `Smart Re-Packager` reads all fragments of one order.
6. It merges item contents, fluid contents, and routing hints.
7. It outputs one or more `Brass Box` items.
8. `Smart Unpackager` reads one `Brass Box` and distributes its contents.

The current implementation uses:

- `FactoryPanelBehaviourMixin` to attach routing at order generation
- `PackagerBlockEntityMixin` to copy routing onto normal packages
- `CanFillerBlockEntity` to copy routing onto `Copper Can`

## 6. Smart Re-Packager Logic

### Input Scan

The block scans the inventory behind it and only considers package items.

Behavior:

- non-fragmented package:
  - passed through unchanged
- fragmented package:
  - grouped by `orderId`
- when a full order is detected:
  - all fragments of that order are removed
  - one or more `Brass Box` outputs are created

### Order Completion

Order completion follows Create's fragment model:

- `orderId`
- `linkIndex`
- `fragmentIndex`
- `isFinalLink`
- `isFinal`

The system must only repack once the full order graph is present.

### Repack Result

All fragments of one order are merged into:

- flattened item stacks
- flattened fluid entries
- routing metadata
- package address

The merged output is chunked into one or more `Brass Box` items, respecting:

- max `9` item slots per box
- max `4` fluid kinds per box
- max `4000 mB` per fluid entry

## 7. Brass Box Data Model

### Item payload

Items are stored using Create's package contents component.

### Fluid payload

Fluids are stored in `BrassBoxFluidContent`.

Each fluid entry contains:

- `FluidStack`
- `ContentRoute`

Important invariant:

- empty fluid entries must never be serialized
- `0 mB` / `minecraft:empty` entries are invalid

This was a real bug during development and is now explicitly filtered out.

### Routing payload

Routing is stored in `BrassBoxRoutingData`.

It contains:

- item routing entries, keyed by item prototype
- fluid routing entries, keyed by fluid id

The granularity is by kind, not by partial quantity.

## 8. Smart Unpackager Logic

### Acceptance model

`Smart Unpackager` never partially accepts a `Brass Box`.

It first computes whether the entire box can be distributed successfully.

If not, it leaves the box untouched.

### Output preferences

Preferred routing is:

- `UP` -> upper output
- `DOWN` -> side output
- `NONE` -> side output

### Fallback behavior

Routing is preference-based, not absolute.

Fallback behavior:

- if preferred output cannot accept a specific content entry, try the other output
- if no full solution exists for the whole box, reject the box

This means:

- `UP` content may fall back to side
- `DOWN` or `NONE` content may fall back to upper

Example:

- no side container
- upper target is a Basin or Spout-compatible setup that can accept both item and fluid outcomes
- whole box may be routed upward

### Planning before execution

Current implementation performs:

1. build an unpack plan
2. reserve expected item/fluid space virtually
3. if the plan is valid, execute the plan
4. only remove the input `Brass Box` when execution succeeds

This avoids silent loss from optimistic partial execution.

### Depot-specific rule

A Create `Depot` is not treated as a normal stack inventory during planning.

It behaves as a single working slot.

This special handling was added because generic stack-style reservation caused a real bug where:

- fluid entered the upper target
- side item insertion failed on a Depot already holding an item
- the package was still consumed

Current behavior is conservative:

- if a Depot is already occupied, a second item cannot be virtually reserved there

## 9. Flexible Upper Connection

The prototype now supports a functional flex-link for the upper output.

### Current interaction

Using the mod's `Baton`:

1. right-click `Smart Unpackager`
2. right-click target block
3. target position and clicked face become the upper output target

Sneak-right-clicking the same `Smart Unpackager` with the baton clears the link.

### Current storage

The block entity stores:

- `flexibleTargetPos`
- `flexibleTargetFace`

At runtime:

- if a flex target exists, upper output uses that target and face
- otherwise it falls back to the block directly above with `Direction.DOWN`

### Current limitations

This is functional only.

Not yet implemented:

- cable model
- rendered connection line
- highlight preview
- dedicated max-range UX beyond status message

## 10. Known Prototype Constraints

These are intentional or not yet polished:

- no final visual cable model yet
- no animation for Smart Unpackager
- routing metadata currently focuses on Factory Gauge generated orders
- zh-CN localization for the new prototype content is not fully cleaned up
- unpack planning is conservative for safety, especially around unusual handlers like Depot

## 11. Important Files

### Main runtime files

- `src/main/java/com/adonis/fluid/block/SmartRepackager/SmartRepackagerBlockEntity.java`
- `src/main/java/com/adonis/fluid/block/SmartUnpackager/SmartUnpackagerBlockEntity.java`
- `src/main/java/com/adonis/fluid/item/BrassBoxItem.java`

### Routing and order injection

- `src/main/java/com/adonis/fluid/mixin/FactoryPanelBehaviourMixin.java`
- `src/main/java/com/adonis/fluid/mixin/PackagerBlockEntityMixin.java`
- `src/main/java/com/adonis/fluid/logistics/manager/MixedOrderRoutingManager.java`
- `src/main/java/com/adonis/fluid/block/CanFiller/CanFillerBlockEntity.java`

### Data components

- `src/main/java/com/adonis/fluid/datacomponent/BrassBoxFluidContent.java`
- `src/main/java/com/adonis/fluid/datacomponent/BrassBoxRoutingData.java`
- `src/main/java/com/adonis/fluid/registry/CFDataComponents.java`

### Link interaction

- `src/main/java/com/adonis/fluid/handler/SmartUnpackagerLinkHandler.java`

## 12. Current Status

At the time of writing, the prototype supports:

- routing hint generation from Factory Gauge layout
- propagation of routing hints through item and fluid package creation
- smart repackaging into `Brass Box`
- pure item repackaging
- pure fluid repackaging
- mixed item+fluid repackaging
- smart unpack routing with fallback
- baton-configured flexible upper target

This is now a functional prototype foundation, not just a concept stub.
