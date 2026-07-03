UPDATE library
SET icon_type = 'LUCIDE'
WHERE icon_type = 'CUSTOM_SVG'
  AND icon IN (
    'atom', 'banana', 'beef', 'brain', 'chef-hat',
    'drama', 'ferris-wheel', 'flame-kindling', 'ghost', 'hamburger',
    'plane', 'rocket', 'roller-coaster', 'rose', 'skull',
    'snail', 'swords', 'tent-tree', 'tree-palm', 'turntable'
  );

UPDATE shelf
SET icon_type = 'LUCIDE'
WHERE icon_type = 'CUSTOM_SVG'
  AND icon IN (
    'atom', 'banana', 'beef', 'brain', 'chef-hat',
    'drama', 'ferris-wheel', 'flame-kindling', 'ghost', 'hamburger',
    'plane', 'rocket', 'roller-coaster', 'rose', 'skull',
    'snail', 'swords', 'tent-tree', 'tree-palm', 'turntable'
  );

UPDATE magic_shelf
SET icon_type = 'LUCIDE'
WHERE icon_type = 'CUSTOM_SVG'
  AND icon IN (
    'atom', 'banana', 'beef', 'brain', 'chef-hat',
    'drama', 'ferris-wheel', 'flame-kindling', 'ghost', 'hamburger',
    'plane', 'rocket', 'roller-coaster', 'rose', 'skull',
    'snail', 'swords', 'tent-tree', 'tree-palm', 'turntable'
  );
