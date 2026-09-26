The project shape is a project's avatar: one Material shape, one tone and the project's initial.

## Anatomy
- A shape from `MaterialShapes`: Cookie9Sided, Cookie6Sided, Clover4Leaf, Pentagon, Gem, Sunny, Pill, Arch or Flower.
- A tone pair from: `primary-container`, `secondary-container`, `tertiary-container`, `primary-fixed` or `tertiary-fixed`, each with its `on-` ink.
- The initial in Google Sans Flex 600 with `ROND` 100, at 40% of the size.
- Sizes: 16 (subtitles, chips), 18 (filter chips), 40 (rows), 48 (project cards), 64 (project header).

## Compose
```kotlin
val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.Pill, MaterialShapes.Arch, MaterialShapes.Flower)
val h = project.id.hashCode().absoluteValue
Box(Modifier.size(40.dp).clip(shapes[h % shapes.size].toShape()).background(tones[h / 9 % tones.size].container), contentAlignment = Alignment.Center) { Text(initial, style = titleMedium.copy(fontVariationSettings = rond100)) }
```

## Rules
- Derive the shape and tone from the id, so they stay the same across devices and reinstalls. The user may override them in project settings.
- The "No project" scratch chats always use a Pentagon in `surface-container-highest`.
- Never use a project shape as a button or status. It's identity only.
