"""Generate code-native block geometry. Uses vanilla door transforms for matching collision."""
import json
import math
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/horror'
CLIENT = Path.home() / '.gradle/caches/fabric-loom/26.3/minecraft-client.jar'


def write(path, value):
    dest = ASSETS / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def box(start, end, texture='surface', rotation=None, glow=False):
    element = {'from': start, 'to': end, 'faces': {
        face: {'texture': '#' + texture} for face in ('up', 'down', 'north', 'south', 'east', 'west')}}
    if rotation:
        element['rotation'] = rotation
    if glow:
        element['light_emission'] = 13
    return element


def model(name, elements, textures):
    write(f'models/block/{name}.json', {'parent': 'minecraft:block/block',
          'textures': {'particle': textures['surface'], **textures}, 'elements': elements})


for name in ('concrete', 'floor'):
    write(f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all',
          'textures': {'all': 'horror:block/concrete'}})
    # Quarter turns disrupt recognizable repetition without introducing a grid border.
    write(f'blockstates/{name}.json', {'variants': {'': [
        {'model': f'horror:block/{name}', 'y': angle} for angle in (0, 90, 180, 270)]}})

metal = 'minecraft:block/gray_concrete'
# Eight thin longitudinal panels form a closed octagonal tube of diameter 0.25 m.
pipe = []
r = 2
half_side = r * math.tan(math.pi / 8)
for angle in (-45, 0, 45):
    for side in (-1, 1):
        center_y = 11 + side * (r - .10)
        pipe.append(box([3-half_side, center_y-.1, 0], [3+half_side, center_y+.1, 16],
                        rotation={'origin': [3, 11, 8], 'axis': 'z', 'angle': angle}))
for side in (-1, 1):
    center_x = 3 + side * (r - .10)
    pipe.append(box([center_x-.1, 11-half_side, 0], [center_x+.1, 11+half_side, 16]))
model('pipe', pipe, {'surface': metal})
model('skirting', [box([0, 0, 0], [.75, 2, 16])], {'surface': metal})
model('ceiling_light', [box([4, 14.75, 0], [12, 16, 16]),
      box([4.75, 14, .5], [11.25, 14.75, 15.5], 'diffuser', glow=True)],
      {'surface': metal, 'diffuser': 'minecraft:block/white_concrete'})
# Unpowered fixture: grey diffuser without emission.
model('ceiling_light_off', [box([4, 14.75, 0], [12, 16, 16]),
      box([4.75, 14, .5], [11.25, 14.75, 15.5], 'diffuser')],
      {'surface': metal, 'diffuser': 'minecraft:block/light_gray_concrete'})

for name in ('pipe', 'skirting'):
    write(f'blockstates/{name}.json', {'variants': {'': {'model': f'horror:block/{name}'}}})
write('blockstates/ceiling_light.json', {'variants': {
    'lit=true': {'model': 'horror:block/ceiling_light'},
    'lit=false': {'model': 'horror:block/ceiling_light_off'}}})

# Flashlight glow is invisible; the model only satisfies the model loader.
write('blockstates/flashlight_glow.json', {'variants': {'': {'model': 'minecraft:block/air'}}})
write('models/item/flashlight.json', {'parent': 'minecraft:item/handheld',
      'textures': {'layer0': 'horror:item/flashlight'}})
write('items/flashlight.json', {'model': {'type': 'minecraft:model', 'model': 'horror:item/flashlight'}})

with ZipFile(CLIENT) as jar:
    states = json.loads(jar.read('assets/minecraft/blockstates/iron_door.json'))
    for value in states['variants'].values():
        value['model'] = value['model'].replace('minecraft:block/iron_door', 'horror:block/service_door')
    write('blockstates/service_door.json', states)
    for half in ('bottom', 'top'):
        for hinge in ('left', 'right'):
            for opened in ('', '_open'):
                suffix = f'{half}_{hinge}{opened}'
                geometry = json.loads(jar.read(f'assets/minecraft/models/block/door_{suffix}.json'))
                geometry['textures'] = {'particle': metal, 'bottom': metal, 'top': metal,
                                        'handle': 'minecraft:block/light_gray_concrete'}
                # Add a small lever on each side, following the vanilla panel orientation.
                if half == 'bottom':
                    panel = geometry['elements'][0]
                    lo, hi = panel['from'], panel['to']
                    axis = 0 if hi[0] - lo[0] == 3 else 2
                    along = 2 if axis == 0 else 0
                    start = list(lo)
                    end = list(hi)
                    start[1], end[1] = 12, 12.75
                    start[along], end[along] = 10, 14
                    start[axis], end[axis] = max(-1, lo[axis]-1), min(17, hi[axis]+1)
                    geometry['elements'].append(box(start, end, 'handle'))
                write(f'models/block/service_door_{suffix}.json', geometry)

names = {'concrete': ('Continuous concrete', 'コンクリート壁'),
         'floor': ('Concrete floor', 'コンクリート床'), 'pipe': ('Octagonal pipe (Z axis)', '配管（Z方向）'),
         'skirting': ('Skirting (west wall)', '巾木（西側の壁用）'),
         'ceiling_light': ('Ceiling light', '天井照明'), 'service_door': ('Service door', '管理用ドア')}
for locale, index in [('en_us', 0), ('ja_jp', 1)]:
    write(f'lang/{locale}.json', {**{f'block.horror.{k}': v[index] for k, v in names.items()},
          'block.horror.flashlight_glow': ('Flashlight glow', '懐中電灯の光')[index],
          'item.horror.flashlight': ('Flashlight', '懐中電灯')[index],
          'key.horror.surface': ('Toggle horror visuals', 'ホラー描画の切り替え')[index],
          'key.category.horror.visuals': ('Horror: Visuals', 'Horror：描画')[index],
          'message.horror.surface_on': ('Horror visuals: on', 'ホラー描画：オン')[index],
          'message.horror.surface_off': ('Horror visuals: off (vanilla rendering)', 'ホラー描画：オフ（標準の描画）')[index],
          'pack.horror.visuals': ('Horror Visuals', 'ホラー描画')[index],
          'pack.horror.visuals.description': ('Terrain shape, materials, fog and colour grading',
                                              '地形の形状・素材・霧・色調')[index]})
for name in names:
    block_model = name if name != 'service_door' else 'service_door_bottom_left'
    write(f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'horror:block/{block_model}'}})
print('Generated interior and flashlight models, blockstates, item definitions and translations.')
