import json, os, sys, shutil
from PIL import Image
V = sys.argv[1]; OUT = sys.argv[2]
# key, base texture, palette (어두움 -> 밝음), keep_handle, parent
ITEMS = [
 # key, 바탕 텍스쳐, 팔레트(어두움 -> 밝음), 손잡이 유지, 모델, 보석 색, 룬 색
 # 무기
 ("claymore_sd","golden_sword",["#4A3000","#D49A00","#FFE07A","#FFFCE0"],True,"handheld","#4FC3F7","#FFF6C0"),
 ("claymore_js","golden_sword",["#3E0B0B","#A8770A","#EEC955","#FFF4C8"],True,"handheld","#E53935","#FFE0A0"),
 ("frost_grave","diamond_sword",["#082640","#2785C0","#8FD6F5","#F0FBFF"],True,"handheld","#E8FBFF","#C8F2FF"),
 ("sealed_sword","diamond_sword",["#06263F","#176FAE","#7FC4F0","#ECF7FF"],True,"handheld","#FFFFFF","#B8E6FF"),
 ("quantum_sword","diamond_sword",["#3E3109","#A88624","#E2CD7E","#FDFBF0"],True,"handheld","#B388FF","#FFF5D6"),
 ("ember_brand","diamond_sword",["#330B00","#B3390A","#F28A3A","#FFDDBB"],True,"handheld","#FFE14D","#FFC38A"),
 ("soul_splitter","netherite_sword",["#140A22","#45306E","#8669BC","#D8CBFF"],True,"handheld","#69F0AE","#CDBDFF"),
 ("chronos","netherite_sword",["#332914","#857246","#C8B585","#ECF0E0"],True,"handheld","#9BD4A2","#FFE6E0"),
 ("thunder_axe","iron_axe",["#232D0B","#7F9726","#CFE27E","#E8FAFF"],True,"handheld","#FFF176","#E0F7FF"),
 ("ruiner_scythe","netherite_hoe",["#1C0008","#7E0818","#CC3A44","#FFAAAA"],True,"handheld","#FF1744","#FF8A80"),
 ("chaos_apocalypse","netherite_hoe",["#0A0A0C","#36363C","#83838C","#DEDEE2"],True,"handheld","#B388FF","#D8D8E0"),
 ("aristotle_pickaxe","netherite_pickaxe",["#0A2935","#1B6C86","#73BCD1","#ECF5F7"],True,"handheld","#FFFFFF","#B2EBF2"),
 ("void_bow","bow",["#18191E","#4D4F57","#9EA1AA","#F0F0F0"],False,"bow","#B388FF","#FFFFFF"),
 ("time_piecer_wind","breeze_rod",["#33291A","#94804F","#D5C59B","#F6F1E4"],False,"handheld_rod","#9BD4A2","#FFF3D6"),
 ("echo_bell","bell",["#241F38","#665A9E","#B3A8DD","#F0EDFF"],False,"generated","#E1D9FF","#E1D9FF"),
 ("umbral_chain","chain",["#0D0D12","#2E2E44","#666684","#B3B3C8"],False,"generated","#B388FF","#B388FF"),
 ("darkness_wave","music_disc_11",["#09090B","#34343A","#77777F","#CBCBD0"],False,"generated","#B388FF","#B388FF"),
 ("new_characteristic","music_disc_5",["#10133E","#3942A6","#959BE2","#FAD4D4"],False,"generated","#FAD4D4","#FAD4D4"),
 # 방어구
 ("void_chest","netherite_chestplate",["#18191E","#4D4F57","#9EA1AA","#F0F0F0"],False,"generated","#B388FF","#D8D8E0"),
 ("russell_helmet","netherite_helmet",["#0A2935","#1B6C86","#73BCD1","#ECF5F7"],False,"generated","#FFFFFF","#B2EBF2"),
 ("chaos_helmet","netherite_helmet",["#0A0A0C","#36363C","#83838C","#DEDEE2"],False,"generated","#B388FF","#D8D8E0"),
 ("ruiner_pants","netherite_leggings",["#1C0008","#7E0818","#CC3A44","#FFAAAA"],False,"generated","#FF1744","#FF8A80"),
 ("sealed_wings","elytra",["#06263F","#176FAE","#7FC4F0","#ECF7FF"],False,"generated","#FFFFFF","#B8E6FF"),
 ("aurora_boots","diamond_boots",["#3E3109","#A88624","#E2CD7E","#FDFBF0"],False,"generated","#B388FF","#FFF5D6"),
 ("nature_boots","diamond_boots",["#263A00","#739A00","#C2E35E","#EEFFD2"],False,"generated","#FFE14D","#EEFFD2"),
 # 교환권 · 이동권 (밀랍 봉인) · 재료 (결정 · 광석) · 키 (열쇠 모양) · 표식
 ("legend_voucher","paper",["#5C4A2E","#C9B48A","#EDE2C6","#FFFBF0"],False,"generated","#43A047","#43A047"),
 ("mythic_voucher","paper",["#5C4A2E","#C9B48A","#EDE2C6","#FFFBF0"],False,"generated","#FFB300","#FFB300"),
 ("altar_ticket","paper",["#5C4A2E","#C9B48A","#EDE2C6","#FFFBF0"],False,"generated","#4FC3F7","#4FC3F7"),
 ("time_shard","echo_shard",["#33291A","#94804F","#D5C59B","#F6F1E4"],False,"generated","#9BD4A2","#FFF3D6"),
 ("quantum_matter","amethyst_shard",["#3E3109","#A88624","#E2CD7E","#FDFBF0"],False,"generated","#FFFFFF","#FFFFFF"),
 ("vibranium","netherite_scrap",["#262012","#62521E","#AA924E","#E9E0C6"],False,"generated","#FFFFFF","#FFFFFF"),
 ("weapon_key","trial_key",["#1B3442","#4787A8","#A2D2EA","#EEF8FF"],False,"generated","#FFE14D","#FFFFFF"),
 ("heaven_key","trial_key",["#3A3A40","#8C8C94","#D2D2D8","#FFFFFF"],False,"generated","#43E07A","#FF5252"),
 ("sealed_key","trial_key",["#06263F","#176FAE","#7FC4F0","#ECF7FF"],False,"generated","#FFFFFF","#B8E6FF"),
 ("ruiner_key","trial_key",["#1C0008","#7E0818","#CC3A44","#FFAAAA"],False,"generated","#FF1744","#FF8A80"),
 ("crown_mark","nether_star",["#4A3000","#D49A00","#FFE07A","#FFFCE0"],False,"generated","#E53935","#FFFCE0"),
]

# RPG 장식 위치 (바탕 텍스쳐마다): gem = 보석, gem2 = 보석 그림자, rune = 빛나는 룬, pommel = 손잡이 끝 보석
ACCENTS = {
 "sword": {"gem":[(4,8)],"gem2":[(5,9)],"rune":[(12,3),(10,5),(8,7)],"pommel":[(1,14)]},
 "netherite_hoe": {"gem":[(12,4)],"rune":[(7,2),(9,2)],"pommel":[(2,14)]},
 "netherite_pickaxe": {"gem":[(12,4)],"rune":[(7,3),(9,3),(13,7),(13,9)],"pommel":[(2,14)]},
 "iron_axe": {"gem":[(12,5)],"rune":[(9,4),(10,6)],"pommel":[(2,14)]},
 "bow": {"gem":[(3,9)]},
 "breeze_rod": {"gem":[(12,1)],"rune":[(10,4),(8,6),(6,8),(4,10)]},
 "netherite_helmet": {"gem":[(7,5),(8,5)]},
 "netherite_chestplate": {"gem":[(7,9),(8,9)],"rune":[(7,11),(8,11)]},
 "netherite_leggings": {"gem":[(7,3),(8,3)]},
 "diamond_boots": {"gem":[(4,9),(11,9)]},
 "elytra": {"gem":[(5,4),(10,4)]},
 "paper": {"gem":[(7,8),(8,8),(7,9)],"gem2":[(8,9)]},
 "trial_key": {"gem":[(7,3),(8,3)],"rune":[(8,12)]},
 "nether_star": {"gem":[(7,8)]},
 "music_disc_11": {"gem":[(7,7)]},
 "music_disc_5": {"gem":[(7,7)]},
}
for _b in ("diamond_sword","golden_sword","netherite_sword"): ACCENTS[_b] = ACCENTS["sword"]
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
def shade(c,f): return tuple(max(0,min(255,round(v*f))) for v in c)
def decorate(im, base, gem, rune):
    """보석 · 룬 · 손잡이 끝 보석 (이미 그려진 칸에만)"""
    acc=ACCENTS.get(base)
    if not acc: return im
    px=im.load(); g=hx(gem); r=hx(rune)
    def put(pts,c):
        for x,y in pts:
            if px[x,y][3]: px[x,y]=(*c,255)
    put(acc.get("gem",[]),g); put(acc.get("gem2",[]),shade(g,0.6)); put(acc.get("pommel",[]),g)
    put(acc.get("rune",[]),tuple(round(a*0.55+b*0.45) for a,b in zip(r,px[acc["rune"][0]][:3])) if acc.get("rune") and px[acc["rune"][0]][3] else r)
    return im
for key,base,stops,keep,parent,gem,rune in ITEMS:
    decorate(recolor(os.path.join(V,base+'.png'),stops,keep),base,gem,rune).save(os.path.join(A,'textures/item',key+'.png'))
    model(key,key,parent)
    m=lambda k:{'type':'minecraft:model','model':'wave:item/'+k}
    if parent=='bow':
        for i in range(3):
            decorate(recolor(os.path.join(V,'bow_pulling_%d.png'%i),stops,keep),base,gem,rune).save(os.path.join(A,'textures/item',key+'_pulling_%d.png'%i))
            model(key+'_pulling_%d'%i,key+'_pulling_%d'%i,'bow')
        d={'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':m(key),
            'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':0.05,
                'entries':[{'threshold':0.65,'model':m(key+'_pulling_1')},{'threshold':0.9,'model':m(key+'_pulling_2')}],
                'fallback':m(key+'_pulling_0')}}}
    else:
        d={'model':m(key)}
    json.dump(d,open(os.path.join(A,'items',key+'.json'),'w'),indent=1)
json.dump({'pack':{'description':'Wave Online 특별 아이템','pack_format':75,'min_format':[75,0],'max_format':[75,0]}},open(os.path.join(OUT,'pack.mcmeta'),'w'),ensure_ascii=False,indent=1)
print(len(ITEMS),'items')
