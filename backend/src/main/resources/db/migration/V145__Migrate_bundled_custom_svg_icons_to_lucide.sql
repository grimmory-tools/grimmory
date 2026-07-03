SET @custom_svg_icon_type = 'CUSTOM_SVG';
SET @lucide_icon_type = 'LUCIDE';

CREATE TEMPORARY TABLE bundled_custom_svg_icon AS
SELECT icon AS icon_name
FROM library
WHERE 1 = 0;

INSERT INTO bundled_custom_svg_icon (icon_name) VALUES
    ('atom'),
    ('banana'),
    ('beef'),
    ('brain'),
    ('chef-hat'),
    ('drama'),
    ('ferris-wheel'),
    ('flame-kindling'),
    ('ghost'),
    ('hamburger'),
    ('plane'),
    ('rocket'),
    ('roller-coaster'),
    ('rose'),
    ('skull'),
    ('snail'),
    ('swords'),
    ('tent-tree'),
    ('tree-palm'),
    ('turntable');

UPDATE library
INNER JOIN bundled_custom_svg_icon
  ON library.icon = bundled_custom_svg_icon.icon_name
SET library.icon_type = @lucide_icon_type
WHERE library.icon_type = @custom_svg_icon_type;

UPDATE shelf
INNER JOIN bundled_custom_svg_icon
  ON shelf.icon = bundled_custom_svg_icon.icon_name
SET shelf.icon_type = @lucide_icon_type
WHERE shelf.icon_type = @custom_svg_icon_type;

UPDATE magic_shelf
INNER JOIN bundled_custom_svg_icon
  ON magic_shelf.icon = bundled_custom_svg_icon.icon_name
SET magic_shelf.icon_type = @lucide_icon_type
WHERE magic_shelf.icon_type = @custom_svg_icon_type;

DROP TEMPORARY TABLE bundled_custom_svg_icon;
