import Aura from '@primeuix/themes/aura';

type ColorPalette = Record<string, string>;

const customPalettes: Record<string, ColorPalette> = {
  coralSunset: {
    50: '#fef7f0',
    100: '#feede1',
    200: '#fcd9c2',
    300: '#f9be9e',
    400: '#f59673',
    500: '#ef7550',
    600: '#de5a3a',
    700: '#b94730',
    800: '#943a2e',
    900: '#78322a',
    950: '#401814'
  },
  roseBlush: {
    50: '#fef7f7',
    100: '#feebeb',
    200: '#fddcdc',
    300: '#fbbfbf',
    400: '#f69393',
    500: '#ed6767',
    600: '#d93f3f',
    700: '#b73030',
    800: '#982d2d',
    900: '#7f2d2d',
    950: '#451313'
  },
  melonBlush: {
    50: '#fffafb',
    100: '#fff2f4',
    200: '#ffdfe7',
    300: '#ffcbd4',
    400: '#ffaebb',
    500: '#ff90a2',
    600: '#ff6f88',
    700: '#e8506b',
    800: '#b7384d',
    900: '#7f2231',
    950: '#3f1118'
  },
  cottonCandy: {
    50: '#fff9fb',
    100: '#fff0f6',
    200: '#ffdfee',
    300: '#ffc0e0',
    400: '#ffa3cf',
    500: '#ff86bf',
    600: '#ff69ad',
    700: '#e14f93',
    800: '#b93a72',
    900: '#86284f',
    950: '#42142a'
  },
  apricotSunrise: {
    50: '#fffaf7',
    100: '#fff1e8',
    200: '#ffe2c9',
    300: '#ffd2a8',
    400: '#ffbf88',
    500: '#ffad68',
    600: '#ff974f',
    700: '#e67a3b',
    800: '#b6572b',
    900: '#7f351b',
    950: '#3f190f'
  },
  antiqueBronze: {
    50: '#faf7f2',
    100: '#f4ede0',
    200: '#e8d9c0',
    300: '#d9c199',
    400: '#c8a56f',
    500: '#b8904f',
    600: '#a67c44',
    700: '#8a6439',
    800: '#715233',
    900: '#5c442c',
    950: '#302316'
  },
  butteryYellow: {
    50: '#fffef7',
    100: '#fffceb',
    200: '#fff7d1',
    300: '#ffeea7',
    400: '#ffe072',
    500: '#ffcf45',
    600: '#ffb61f',
    700: '#ff9b0a',
    800: '#cc7606',
    900: '#a35f0c',
    950: '#633204'
  },
  vanillaCream: {
    50: '#fefdfb',
    100: '#fefbf6',
    200: '#fdf5ea',
    300: '#fbecd5',
    400: '#f7dbb5',
    500: '#f1c589',
    600: '#e8a95c',
    700: '#d88d3e',
    800: '#b47134',
    900: '#915c2e',
    950: '#4e2f17'
  },
  citrusMint: {
    50: '#fbfff9',
    100: '#f4fff2',
    200: '#e8ffd9',
    300: '#d1ffbc',
    400: '#b7ffa0',
    500: '#96ff84',
    600: '#76e96e',
    700: '#4fb84c',
    800: '#2f7f32',
    900: '#1a4f20',
    950: '#0a2410'
  },
  freshMint: {
    50: '#f8fff9',
    100: '#f0fff3',
    200: '#dcffea',
    300: '#bfffd6',
    400: '#99ffc2',
    500: '#7fffae',
    600: '#5fe592',
    700: '#3fbf75',
    800: '#2d8f55',
    900: '#1e5f39',
    950: '#0f2f1d'
  },
  sagePearl: {
    50: '#fbfdfb',
    100: '#f4f8f4',
    200: '#e6efe6',
    300: '#d1e6d1',
    400: '#bcdcbf',
    500: '#9fcfa8',
    600: '#84b792',
    700: '#5f8f6e',
    800: '#3f6648',
    900: '#25442e',
    950: '#0f2118'
  },
  skyBlue: {
    50: '#f7fbff',
    100: '#eef6ff',
    200: '#dceefd',
    300: '#c1e8ff',
    400: '#99dfff',
    500: '#6fc8ff',
    600: '#48afff',
    700: '#2e88e6',
    800: '#2360b3',
    900: '#153d80',
    950: '#071d40'
  },
  periwinkleCream: {
    50: '#fbfbff',
    100: '#f2f3ff',
    200: '#e7eaff',
    300: '#d2dcff',
    400: '#b9c9ff',
    500: '#9fb2ff',
    600: '#7f97ff',
    700: '#5f78e6',
    800: '#4559b3',
    900: '#2b387f',
    950: '#151c40'
  },
  pastelRoyalBlue: {
    50: '#f6fbff',
    100: '#eef6ff',
    200: '#dbeeff',
    300: '#b9ddff',
    400: '#92c7ff',
    500: '#63aaff',
    600: '#3b88ff',
    700: '#2e66cc',
    800: '#235099',
    900: '#183366',
    950: '#0d1a33'
  },
  lavenderDream: {
    50: '#fbf7ff',
    100: '#f6eeff',
    200: '#ebdcff',
    300: '#dcc0ff',
    400: '#caa1ff',
    500: '#b47fff',
    600: '#9366e6',
    700: '#6f45b3',
    800: '#4c2f80',
    900: '#2d1b4f',
    950: '#15082a'
  },
  dustyNeutral: {
    50: '#faf9f7',
    100: '#f3f1ed',
    200: '#e8e3db',
    300: '#d8cfc2',
    400: '#c4b5a3',
    500: '#b39c85',
    600: '#a28a70',
    700: '#87725d',
    800: '#6f5e4e',
    900: '#5a4e42',
    950: '#2f2821'
  }
};

if (!Aura.primitive) {
  Aura.primitive = {};
}

Object.assign(Aura.primitive, customPalettes);

export default Aura;
