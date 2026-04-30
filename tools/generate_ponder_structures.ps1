Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-Int16BE($bw, [int]$value) {
  $bytes = [BitConverter]::GetBytes([int16]$value)
  [Array]::Reverse($bytes)
  $bw.Write($bytes)
}

function Write-Int32BE($bw, [int]$value) {
  $bytes = [BitConverter]::GetBytes([int32]$value)
  [Array]::Reverse($bytes)
  $bw.Write($bytes)
}

function Write-StringBE($bw, [string]$value) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($value)
  Write-Int16BE $bw $bytes.Length
  if ($bytes.Length -gt 0) {
    $bw.Write($bytes)
  }
}

function Write-Tag($bw, [byte]$type, [string]$name, $value) {
  $bw.Write($type)
  Write-StringBE $bw $name
  Write-TagPayload $bw $type $value
}

function Write-TagPayload($bw, [byte]$type, $value) {
  switch ($type) {
    3 { Write-Int32BE $bw $value }
    8 { Write-StringBE $bw ([string]$value) }
    9 {
      $childType = [byte]$value.ChildType
      $items = @($value.Items)
      $bw.Write($childType)
      Write-Int32BE $bw $items.Count
      foreach ($item in $items) {
        Write-TagPayload $bw $childType $item
      }
    }
    10 {
      foreach ($entry in $value) {
        Write-Tag $bw $entry.Type $entry.Name $entry.Value
      }
      $bw.Write([byte]0)
    }
    default { throw "Unsupported write tag type $type" }
  }
}

function New-TagDef([byte]$type, [string]$name, $value) {
  [pscustomobject]@{ Type = $type; Name = $name; Value = $value }
}

function New-Compound($entries) {
  ,@($entries)
}

function New-List([byte]$childType, $items) {
  [pscustomobject]@{ ChildType = $childType; Items = @($items) }
}

function Write-Structure($path, [int[]]$size, $paletteDefs, $blockDefs) {
  $palette = @()
  $paletteIndex = @{}

  foreach ($def in $paletteDefs) {
    $paletteIndex[$def.Key] = $palette.Count
    $entries = @(New-TagDef 8 'Name' $def.Name)

    if ($def.Properties.Count -gt 0) {
      $propEntries = @()
      foreach ($prop in $def.Properties.GetEnumerator()) {
        $propEntries += New-TagDef 8 $prop.Key $prop.Value
      }
      $entries += New-TagDef 10 'Properties' (New-Compound $propEntries)
    }

    $palette += ,(New-Compound $entries)
  }

  $blocks = @()
  foreach ($b in $blockDefs) {
    $blocks += ,(New-Compound @(
      (New-TagDef 9 'pos' (New-List 3 $b.Pos)),
      (New-TagDef 3 'state' $paletteIndex[$b.State])
    ))
  }

  $root = New-Compound @(
    (New-TagDef 9 'size' (New-List 3 $size)),
    (New-TagDef 9 'blocks' (New-List 10 $blocks)),
    (New-TagDef 9 'palette' (New-List 10 $palette)),
    (New-TagDef 9 'entities' (New-List 10 @())),
    (New-TagDef 3 'DataVersion' 3465)
  )

  $fs = [IO.File]::Create($path)
  try {
    $gz = New-Object IO.Compression.GZipStream($fs, [IO.Compression.CompressionLevel]::Optimal)
    try {
      $bw = New-Object IO.BinaryWriter($gz)
      $bw.Write([byte]10)
      Write-StringBE $bw ''
      Write-TagPayload $bw 10 $root
      $bw.Flush()
    } finally {
      $gz.Dispose()
    }
  } finally {
    $fs.Dispose()
  }
}

function Floor-Blocks([int]$sx, [int]$sz) {
  $blocks = @()
  for ($x = 0; $x -lt $sx; $x++) {
    for ($z = 0; $z -lt $sz; $z++) {
      $state = if ((($x + $z) % 2) -eq 0) { 'white_concrete' } else { 'snow_block' }
      $blocks += [pscustomobject]@{ Pos = @($x, 0, $z); State = $state }
    }
  }
  return $blocks
}

$palette = @(
  [pscustomobject]@{ Key = 'white_concrete'; Name = 'minecraft:white_concrete'; Properties = @{} },
  [pscustomobject]@{ Key = 'snow_block'; Name = 'minecraft:snow_block'; Properties = @{} },
  [pscustomobject]@{ Key = 'belt_start'; Name = 'create:belt'; Properties = [ordered]@{ casing = 'false'; facing = 'east'; part = 'start'; slope = 'horizontal'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'belt_middle'; Name = 'create:belt'; Properties = [ordered]@{ casing = 'false'; facing = 'east'; part = 'middle'; slope = 'horizontal'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'belt_end'; Name = 'create:belt'; Properties = [ordered]@{ casing = 'false'; facing = 'east'; part = 'end'; slope = 'horizontal'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'depot'; Name = 'create:depot'; Properties = [ordered]@{ waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'casing'; Name = 'create:andesite_casing'; Properties = @{} },
  [pscustomobject]@{ Key = 'atomizer_north'; Name = 'fluid:fluid_atomizer'; Properties = [ordered]@{ facing = 'north' } },
  [pscustomobject]@{ Key = 'cog_z'; Name = 'create:cogwheel'; Properties = [ordered]@{ axis = 'z'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'large_cog_z'; Name = 'create:large_cogwheel'; Properties = [ordered]@{ axis = 'z'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'tank_bottom'; Name = 'create:fluid_tank'; Properties = [ordered]@{ bottom = 'true'; shape = 'window'; top = 'false' } },
  [pscustomobject]@{ Key = 'tank_middle'; Name = 'create:fluid_tank'; Properties = [ordered]@{ bottom = 'false'; shape = 'window'; top = 'false' } },
  [pscustomobject]@{ Key = 'tank_top'; Name = 'create:fluid_tank'; Properties = [ordered]@{ bottom = 'false'; shape = 'window'; top = 'true' } },
  [pscustomobject]@{ Key = 'creative_tank_single'; Name = 'create:creative_fluid_tank'; Properties = [ordered]@{ bottom = 'true'; shape = 'window'; top = 'true' } },
  [pscustomobject]@{ Key = 'vessel_x'; Name = 'fluid:communicating_vessel'; Properties = [ordered]@{ axis = 'x' } }
)

$atomizerBlocks = @(Floor-Blocks 7 7)
$atomizerBlocks += @(
  [pscustomobject]@{ Pos = @(1, 1, 2); State = 'belt_start' },
  [pscustomobject]@{ Pos = @(2, 1, 2); State = 'belt_middle' },
  [pscustomobject]@{ Pos = @(3, 1, 2); State = 'belt_middle' },
  [pscustomobject]@{ Pos = @(4, 1, 2); State = 'belt_middle' },
  [pscustomobject]@{ Pos = @(5, 1, 2); State = 'belt_end' },
  [pscustomobject]@{ Pos = @(3, 1, 3); State = 'depot' },
  [pscustomobject]@{ Pos = @(3, 1, 5); State = 'casing' },
  [pscustomobject]@{ Pos = @(3, 2, 5); State = 'atomizer_north' },
  [pscustomobject]@{ Pos = @(3, 2, 6); State = 'cog_z' },
  [pscustomobject]@{ Pos = @(4, 1, 6); State = 'large_cog_z' }
)

$potionBlocks = @(Floor-Blocks 7 7)
$potionBlocks += @(
  [pscustomobject]@{ Pos = @(2, 1, 2); State = 'casing' },
  [pscustomobject]@{ Pos = @(3, 1, 2); State = 'casing' },
  [pscustomobject]@{ Pos = @(4, 1, 2); State = 'casing' },
  [pscustomobject]@{ Pos = @(3, 1, 5); State = 'casing' },
  [pscustomobject]@{ Pos = @(3, 2, 5); State = 'atomizer_north' },
  [pscustomobject]@{ Pos = @(3, 2, 6); State = 'cog_z' },
  [pscustomobject]@{ Pos = @(4, 1, 6); State = 'large_cog_z' },
  [pscustomobject]@{ Pos = @(5, 1, 5); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(5, 2, 5); State = 'tank_middle' },
  [pscustomobject]@{ Pos = @(5, 3, 5); State = 'tank_top' }
)

$vesselBlocks = @(Floor-Blocks 7 7)
$vesselBlocks += @(
  [pscustomobject]@{ Pos = @(0, 1, 1); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(0, 2, 1); State = 'tank_middle' },
  [pscustomobject]@{ Pos = @(0, 3, 1); State = 'tank_top' },
  [pscustomobject]@{ Pos = @(4, 1, 1); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(4, 2, 1); State = 'tank_middle' },
  [pscustomobject]@{ Pos = @(4, 3, 1); State = 'tank_top' },
  [pscustomobject]@{ Pos = @(1, 1, 1); State = 'vessel_x' },
  [pscustomobject]@{ Pos = @(2, 1, 1); State = 'vessel_x' },
  [pscustomobject]@{ Pos = @(3, 1, 1); State = 'vessel_x' },
  [pscustomobject]@{ Pos = @(0, 1, 5); State = 'creative_tank_single' },
  [pscustomobject]@{ Pos = @(4, 1, 5); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(4, 2, 5); State = 'tank_middle' },
  [pscustomobject]@{ Pos = @(4, 3, 5); State = 'tank_top' },
  [pscustomobject]@{ Pos = @(1, 1, 5); State = 'vessel_x' },
  [pscustomobject]@{ Pos = @(2, 1, 5); State = 'vessel_x' },
  [pscustomobject]@{ Pos = @(3, 1, 5); State = 'vessel_x' }
)

$ponderDir = Join-Path $PSScriptRoot '..\\src\\main\\resources\\assets\\fluid\\ponder'
Write-Structure (Join-Path $ponderDir 'fluid_atomizer.nbt') @(7, 4, 7) $palette $atomizerBlocks
Write-Structure (Join-Path $ponderDir 'fluid_atomizer_potion.nbt') @(7, 4, 7) $palette $potionBlocks
Write-Structure (Join-Path $ponderDir 'communicating_vessel.nbt') @(7, 4, 7) $palette $vesselBlocks

# Can Filler scene
$canFillerPalette = @(
  [pscustomobject]@{ Key = 'white_concrete'; Name = 'minecraft:white_concrete'; Properties = @{} },
  [pscustomobject]@{ Key = 'snow_block'; Name = 'minecraft:snow_block'; Properties = @{} },
  [pscustomobject]@{ Key = 'tank_bottom'; Name = 'create:fluid_tank'; Properties = [ordered]@{ bottom = 'true'; shape = 'window'; top = 'false' } },
  [pscustomobject]@{ Key = 'tank_top'; Name = 'create:fluid_tank'; Properties = [ordered]@{ bottom = 'false'; shape = 'window'; top = 'true' } },
  [pscustomobject]@{ Key = 'can_filler_east'; Name = 'fluid:can_filler'; Properties = [ordered]@{ facing = 'east'; linked = 'false'; powered = 'false' } },
  [pscustomobject]@{ Key = 'can_filler_west'; Name = 'fluid:can_filler'; Properties = [ordered]@{ facing = 'west'; linked = 'false'; powered = 'false' } },
  [pscustomobject]@{ Key = 'funnel_push_down'; Name = 'create:andesite_funnel'; Properties = [ordered]@{ extracting = 'true'; facing = 'down'; powered = 'false'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'funnel_pull_down'; Name = 'create:andesite_funnel'; Properties = [ordered]@{ extracting = 'false'; facing = 'down'; powered = 'false'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'belt_start'; Name = 'create:belt'; Properties = [ordered]@{ casing = 'false'; facing = 'east'; part = 'start'; slope = 'horizontal'; waterlogged = 'false' } },
  [pscustomobject]@{ Key = 'belt_end'; Name = 'create:belt'; Properties = [ordered]@{ casing = 'false'; facing = 'east'; part = 'end'; slope = 'horizontal'; waterlogged = 'false' } }
)

$canFillerBlocks = @(Floor-Blocks 9 7)
$canFillerBlocks += @(
  [pscustomobject]@{ Pos = @(1, 1, 2); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(1, 2, 2); State = 'tank_top' },
  [pscustomobject]@{ Pos = @(2, 1, 2); State = 'can_filler_east' },
  [pscustomobject]@{ Pos = @(3, 1, 2); State = 'belt_start' },
  [pscustomobject]@{ Pos = @(4, 1, 2); State = 'belt_end' },
  [pscustomobject]@{ Pos = @(3, 2, 2); State = 'funnel_push_down' },
  [pscustomobject]@{ Pos = @(4, 2, 2); State = 'funnel_pull_down' },
  [pscustomobject]@{ Pos = @(5, 1, 2); State = 'can_filler_west' },
  [pscustomobject]@{ Pos = @(6, 1, 2); State = 'tank_bottom' },
  [pscustomobject]@{ Pos = @(6, 2, 2); State = 'tank_top' }
)

Write-Structure (Join-Path $ponderDir 'can_filler.nbt') @(9, 4, 7) $canFillerPalette $canFillerBlocks
