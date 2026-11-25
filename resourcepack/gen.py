import json, os, sys, shutil
from PIL import Image
V = sys.argv[1]; OUT = sys.argv[2]
# key, base texture, palette (어두움 -> 밝음), keep_handle, parent
ITEMS = [
 # 무기
 ("claymore_sd","golden_sword",["#5C3F00","#E0A800","#FFE680","#FFFCE0"],True,"handheld"),
 ("claymore_js","golden_sword",["#4A0E0E","#B8860B","#F5D35C","#FFF6CC"],True,"handheld"),
 ("frost_grave","diamond_sword",["#0B2E4A","#2E9BD6","#9FE3FF","#F2FCFF"],True,"handheld"),
 ("sealed_sword","diamond_sword",["#08304D","#1C8FD0","#8FD3FF","#EEF8FF"],True,"handheld"),
 ("quantum_sword","diamond_sword",["#4A3B10","#B8962E","#E9D58A","#FDFBF0"],True,"handheld"),
 ("ember_brand","diamond_sword",["#3A0E00","#C2410C","#FF9F43","#FFE2C2"],True,"handheld"),
 ("soul_splitter","netherite_sword",["#160C26","#4C3478","#8E74C4","#DCCFFF"],True,"handheld"),
 ("chronos","netherite_sword",["#3A2F18","#8E7A4E","#CDBB8C","#EEF2E2"],True,"handheld"),
 ("thunder_axe","iron_axe",["#28330E","#8FA82E","#D6E88A","#EAFBFF"],True,"handheld"),
 ("ruiner_scythe","netherite_hoe",["#22000A","#8E0B1C","#D8434B","#FFB3B3"],True,"handheld"),
 ("chaos_apocalypse","netherite_hoe",["#0C0C0E","#3E3E44","#8C8C94","#E2E2E6"],True,"handheld"),
 ("aristotle_pickaxe","netherite_pickaxe",["#0C2F3D","#1F7894","#7CC3D6","#EEF6F8"],True,"handheld"),
 ("void_bow","bow",["#1B1C21","#55575F","#A4A7AF","#F2F2F2"],False,"bow"),
 ("time_piecer_wind","breeze_rod",["#3A3020","#9C8655","#D9C9A0","#F7F2E6"],False,"handheld_rod"),
 ("echo_bell","bell",["#2A2440","#6F62A8","#B9AFE0","#F1EEFF"],False,"generated"),
 ("umbral_chain","chain",["#0F0F14","#33334A","#6E6E8C","#B8B8CC"],False,"generated"),
 ("darkness_wave","music_disc_11",["#0B0B0D","#3A3A40","#7C7C84","#CFCFD4"],False,"generated"),
 ("new_characteristic","music_disc_5",["#121646","#3F4AB0","#9AA0E6","#FAD4D4"],False,"generated"),
 # 방어구
 ("void_chest","netherite_chestplate",["#1B1C21","#55575F","#A4A7AF","#F2F2F2"],False,"generated"),
 ("russell_helmet","netherite_helmet",["#0C2F3D","#1F7894","#7CC3D6","#EEF6F8"],False,"generated"),
 ("chaos_helmet","netherite_helmet",["#0C0C0E","#3E3E44","#8C8C94","#E2E2E6"],False,"generated"),
 ("judge_helmet","netherite_helmet",["#4A0E0E","#B8860B","#F5D35C","#FFF6CC"],False,"generated"),
 ("ruiner_pants","netherite_leggings",["#22000A","#8E0B1C","#D8434B","#FFB3B3"],False,"generated"),
 ("sealed_wings","elytra",["#08304D","#1C8FD0","#8FD3FF","#EEF8FF"],False,"generated"),
 ("aurora_boots","diamond_boots",["#4A3B10","#B8962E","#E9D58A","#FDFBF0"],False,"generated"),
 ("chronos_boots","diamond_boots",["#3A2F18","#8E7A4E","#CDBB8C","#EEF2E2"],False,"generated"),
 ("nature_boots","diamond_boots",["#2E4400","#7FA800","#C9E86A","#F0FFD8"],False,"generated"),
 # 교환권 · 이동권 · 재료 · 키
 ("legend_voucher","paper",["#24501A","#5FA845","#B6E39E","#F2FFEE"],False,"generated"),
 ("mythic_voucher","paper",["#5C3F00","#D8961E","#FFD98A","#FFF8E6"],False,"generated"),
 ("altar_ticket","paper",["#5A4C00","#CDB43C","#F6E8A0","#FFFDEB"],False,"generated"),
 ("time_shard","glowstone_dust",["#3A3020","#9C8655","#D9C9A0","#F7F2E6"],False,"generated"),
 ("quantum_matter","yellow_dye",["#4A3B10","#B8962E","#E9D58A","#FDFBF0"],False,"generated"),
 ("vibranium","orange_dye",["#2E2412","#6E5A22","#B49A55","#ECE4CC"],False,"generated"),
 ("weapon_key","pink_dye",["#1E3A4A","#4E93B8","#A9D8EE","#EEF8FF"],False,"generated"),
 ("heaven_key","gray_dye",["#4A1414","#A04040","#8ED6A0","#EFFFF2"],False,"generated"),
 ("sealed_key","light_blue_dye",["#08304D","#1C8FD0","#8FD3FF","#EEF8FF"],False,"generated"),
 ("ruiner_key","red_dye",["#22000A","#8E0B1C","#D8434B","#FFB3B3"],False,"generated"),
 ("crown_mark","nether_star",["#5C3F00","#D8961E","#FFD98A","#FFF8E6"],False,"generated"),
]
def hx(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
_st = Image.open(os.path.join(V,'stick.png')).convert('RGBA'); _sp = _st.load()
stick = set(_sp[x,y][:3] for y in range(_st.size[1]) for x in range(_st.size[0]) if _sp[x,y][3])
def is_handle(rgb):
    if rgb in stick: return True
    r,g,b = rgb  # 나무 손잡이: 갈색 (빨강 > 초록 > 파랑, 어둡고 채도 있음)
    return r > g > b and r - b > 25 and r < 140
def gmap(t, stops):
    t=max(0,min(1,t)); n=len(stops)-1; i=min(int(t*n), n-1); f=t*n-i
    a,b=hx(stops[i]),hx(stops[i+1]); return tuple(round(a[k]+(b[k]-a[k])*f) for k in range(3))
def recolor(src, stops, keep):
    im=Image.open(src).convert('RGBA'); px=im.load(); w,h=im.size
    pts=[(x,y) for y in range(h) for x in range(w) if px[x,y][3] and not (keep and is_handle(px[x,y][:3]))]
    lum=lambda c:0.299*c[0]+0.587*c[1]+0.114*c[2]
    ls=[lum(px[x,y]) for x,y in pts]
    lo,hi=min(ls),max(ls)
    for (x,y),l in zip(pts,ls):
        r,g,b=gmap((l-lo)/(hi-lo or 1), stops); px[x,y]=(r,g,b,px[x,y][3])
    return im
A=os.path.join(OUT,'assets','wave')
for d in ('items','models/item','textures/item'): os.makedirs(os.path.join(A,d),exist_ok=True)
def model(key,tex,parent):
    p={'handheld':'minecraft:item/handheld','handheld_rod':'minecraft:item/handheld_rod','generated':'minecraft:item/generated','bow':'minecraft:item/bow'}[parent]
    json.dump({'parent':p,'textures':{'layer0':'wave:item/'+tex}},open(os.path.join(A,'models/item',key+'.json'),'w'),indent=1)
for key,base,stops,keep,parent in ITEMS:
    recolor(os.path.join(V,base+'.png'),stops,keep).save(os.path.join(A,'textures/item',key+'.png'))
    model(key,key,parent)
    m=lambda k:{'type':'minecraft:model','model':'wave:item/'+k}
    if parent=='bow':
        for i in range(3):
            recolor(os.path.join(V,'bow_pulling_%d.png'%i),stops,keep).save(os.path.join(A,'textures/item',key+'_pulling_%d.png'%i))
            model(key+'_pulling_%d'%i,key+'_pulling_%d'%i,'bow')
        d={'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':m(key),
            'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':0.05,
                'entries':[{'threshold':0.65,'model':m(key+'_pulling_1')},{'threshold':0.9,'model':m(key+'_pulling_2')}],
                'fallback':m(key+'_pulling_0')}}}
    else:
        d={'model':m(key)}
    json.dump(d,open(os.path.join(A,'items',key+'.json'),'w'),indent=1)
json.dump({'pack':{'description':'Wave Online 특별 아이템','pack_format':75,'min_format':[75,0],'max_format':[75,0]}},open(os.path.join(OUT,'pack.mcmeta'),'w'),ensure_ascii=False,indent=1)
json.dump([k for k,*_ in ITEMS],open(os.path.join(OUT,'..','keys.json'),'w'))
print(len(ITEMS),'items')
