package kr.wave.core;

import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 스크립트에 나오는 모든 커스텀 아이템.
 * 기존 Skript 가 만든 아이템도 이름(색상 제외)으로 인식하므로, 이름 문자열은 원본과 똑같이 유지한다.
 */
public final class Items {

    private Items() {
    }

    private static final String LEGENDARY = "%nl%&7등급 : <gradient:#29FF00:#EEEEEE><bold:true>Legendary</gradient>%nl%%nl%";
    private static final String SEASON_KEEP = "%nl%%nl%&c&l해당 아이템은 시즌이 지나도 유지됩니다.";

    /** 시즌이 지나도 남는 아이템인지 (설명에 "시즌이 지나도" 문구가 있는 아이템 전부) */
    public static boolean isSeasonKeep(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        java.util.List<net.kyori.adventure.text.Component> lore = it.getItemMeta().lore();
        if (lore == null) return false;
        for (net.kyori.adventure.text.Component c : lore) if (Msg.plain(c).contains("시즌이 지나도")) return true;
        return false;
    }

    /**
     * 칸 목록에서 시즌 유지 아이템만 골라 사본으로 모음.
     * 셜커 상자 · 꾸러미(번들) 안에 든 것도 꺼내서 모음 (그 상자 자체가 유지 아이템이면 통째로)
     */
    public static java.util.List<ItemStack> seasonKeepIn(ItemStack[] slots) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        if (slots == null) return out;
        for (ItemStack it : slots) {
            if (it == null || it.getType().isAir()) continue;
            if (isSeasonKeep(it)) {
                out.add(it.clone());
                continue;
            }
            if (it.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
                out.addAll(seasonKeepIn(box.getInventory().getContents()));   // 셜커 상자 안 (안의 꾸러미까지)
            } else if (it.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
                out.addAll(seasonKeepIn(bundle.getItems().toArray(new ItemStack[0])));
            }
        }
        return out;
    }

    /** 이미 지급된 아이템에도 '시즌이 지나도 유지됩니다' 문구를 붙임. 바뀌었으면 true */
    public static boolean ensureSeasonKeep(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        java.util.List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        for (net.kyori.adventure.text.Component c : lore) if (Msg.plain(c).contains("시즌이 지나도 유지")) return false;
        lore = Msg.append(lore, SEASON_KEEP);
        meta.lore(lore);
        it.setItemMeta(meta);
        return true;
    }

    /* ======================= 이름 (색상 제외 비교용) ======================= */

    public static final String N_NEWBIE_PASS = "[💡 NEWBIE] 뉴비 헬퍼 패스";
    public static final String N_CROWN = "[👑] [⭐8] 크라운 처치 알림 표식";
    public static final String N_QUANTUM_SWORD = "[Quantum] [Lv.A] 앱솔루트 퀀텀";
    public static final String N_AURORA_BOOTS = "[Quantum] [Lv.A] 오로라 퀸트라늄";
    public static final String N_PACK_HIGH = "[PVP] 고급 전투 패키지";
    public static final String N_PACK_MID = "[PVP] 중급 전투 패키지";
    public static final String N_PACK_ENCHANT = "[Magic] • 인챈트 패키지 •";
    public static final String N_MELON = "최고급 수박 조각";
    public static final String N_NEWBIE_PACKAGE = "[Newbie] 뉴비 지원 패키지";
    public static final String N_MINERAL_PACK = "[Newbie] 광물 팩";
    public static final String N_PREORDER_ROULETTE = "[Pre-Order] • 시작의 룰렛 •";
    public static final String N_CHECK = "[🪙] 캐시 수표";
    public static final String N_SEALED_SWORD = "[Sealed] [Lv.✕] 얼어붙은 제왕의 검";
    public static final String N_THUNDER = "[⚡] [Lv.L] 천둥의 심판";
    public static final String N_CHAOS = "[Chaos] [Lv.✦] 혼돈의 아포칼립스";
    /** 혼돈의 아포칼립스 세트 */
    public static final String N_DARK_WAVE = "[Chaos] [Lv.✦] 다크니스 웨이브";
    public static final String N_CHAOS_HELM = "[Chaos] [Lv.✦] 종말의 투구";
    public static final String N_RUINER_SCYTHE = "[Ruiner] [Lv.✕] 파괴의 낫";
    public static final String N_RUINER_PANTS = "[Ruiner] [Lv.✕] 재앙의 서막 바지";
    public static final String N_NATURE_BOOTS = "[Natural] [Lv.✦] 대자연의 황혼";
    public static final String N_VOID_CHEST = "[Void] [Lv.✕] 뒤틀린 차원의 갑옷";
    public static final String N_VOID_BOW = "[Void] [Lv.✕] 공허의 속삭임";
    public static final String N_RUSSELL = "[Lv.✕] 러셀의 패러독스";
    public static final String N_ETERNAL_CHRONOS = "[Time] [Lv.↗] 이터널 크로노스 (MAX)";
    public static final String N_CHRONOS = "[Time] [Lv.↗] 크로노스의 칼날";
    public static final String N_TIME_SHARD = "[Time] 시간의 파편";
    public static final String N_RUINER_ESSENCE = "[✦] 파멸자의 봉인된 정수";
    /** 초승달의 서약 (예전 이름 The New Characteristic / 새 시대의 각인 / 신월의 기록 도 같은 무기로 인식, 접속·우클릭 시 새 이름으로 바뀜) */
    public static final String N_NEW_CHAR = "[🌙] [Lv.DD] 초승달의 서약";
    public static final String N_NEW_CHAR_OLD3 = "[🌙] [Lv.DD] 신월의 기록";
    public static final String N_NEW_CHAR_OLD = "The New Characteristic";
    public static final String N_NEW_CHAR_OLD2 = "[🌙] [Lv.DD] 새 시대의 각인";

    private static java.util.Map<String, java.util.function.Supplier<ItemStack>> templates;

    /**
     * 이름(색 없는 글자)이 정확히 같은 특수 아이템의 최신 모습. 접속 시 이름/설명 갱신에 사용.
     * 예전 이름(영어 이름 등)도 새 아이템으로 연결한다. 망령 수확자는 처치 수가 들어가므로 따로 처리.
     */
    public static java.util.function.Supplier<ItemStack> template(String plain) {
        if (templates == null) {
            java.util.Map<String, java.util.function.Supplier<ItemStack>> m = new java.util.HashMap<>();
            java.util.List<java.util.function.Supplier<ItemStack>> all = specialItems();
            for (var sup : all) m.put(Msg.name(sup.get()), sup);
            // 예전 이름
            m.put("[✦] " + N_TIME_WIND_OLD, Items::timePiecerWind);
            m.put("[✦] " + N_SHADE_OLD, Items::umbralChain);
            m.put("[✦] " + N_BELL_OLD, Items::echoBell);
            m.put("[🌙] [Lv.DD] " + N_NEW_CHAR_OLD, Items::newCharacteristic);
            m.put(N_NEW_CHAR_OLD2, Items::newCharacteristic);
            m.put(N_NEW_CHAR_OLD3, Items::newCharacteristic);
            m.put(N_LEGEND_VOUCHER_OLD, Items::legendVoucher);
            m.put(N_MYTHIC_VOUCHER_OLD, Items::mythicVoucher);
            templates = m;
        }
        return templates.get(plain);
    }

    /** 서버 특수 아이템 전체 (접속 시 설명 갱신, /아이템 지급에 같이 씀) */
    public static java.util.List<java.util.function.Supplier<ItemStack>> specialItems() {
        java.util.List<java.util.function.Supplier<ItemStack>> all = new java.util.ArrayList<>(java.util.List.of(
                Items::claymoreSd, Items::frostGrave, Items::newCharacteristic, Items::sealedSword, Items::sealedWings,
                Items::ruinerScythe, Items::ruinerPants, Items::chaosApocalypse, Items::darknessWave, Items::chaosHelmet,
                Items::voidBow, Items::voidChest, Items::russellHelmet, Items::aristotlePickaxe, Items::quantumSword,
                Items::auroraBoots, Items::chronosBoots, Items::natureBoots, Items::thunderAxe, Items::judgeHelmet,
                Items::claymoreJs, Items::emberBrand, Items::umbralChain, Items::echoBell, Items::timePiecerWind,
                () -> melon(1), Items::newbiePass, Items::legendVoucher, Items::mythicVoucher, Items::altarTicket,
                Items::timeShard, Items::quantumMatter, Items::vibranium, Items::ruinerEssence));
        for (int i = 0; i <= 6; i++) {
            final int st = i;
            all.add(() -> chronos(st));
        }
        all.add(Items::crownMark);
        return all;
    }

    public static boolean isNewChar(String plainName) {
        return plainName.contains(N_NEW_CHAR) || plainName.contains(N_NEW_CHAR_OLD) || plainName.contains(N_NEW_CHAR_OLD2) || plainName.contains(N_NEW_CHAR_OLD3);
    }
    public static final String N_CLAYMORE_SD = "[⚡] [Lv.sd] 천공의 심판자 클레이모어";
    public static final String N_FROST = "[❆] [Lv.DD] 프로스트 그레이브";
    /** 보조 무기 3종 (한국어 이름). 예전 영어 이름의 아이템도 같은 무기로 인식하고, 쓰는 순간 새 이름으로 바뀜 */
    public static final String N_TIME_WIND = "[✦] 시간을 가르는 바람";
    public static final String N_TIME_WIND_OLD = "Time piecer Wind";
    public static final String N_BELL = "[✦] 메아리 종";
    public static final String N_BELL_OLD = "Echo Bell";
    public static final String N_SHADE = "[✦] 그림자 사슬";
    public static final String N_SHADE_OLD = "Umbral Chain";
    /** 잿불 낙인검 (일반 상점 판매) */
    public static final String N_EMBER = "잿불 낙인검";
/** 망령 수확자 (예전 Soul Splitter 를 완전히 바꾼 무기). 이름에 이 글자가 있으면 같은 무기 */
    public static final String N_SOUL_SPLITTER = "망령 수확자";
    /** 예전 이름 "[✦] [Counter] Soul Splitter" 의 칼도 같은 무기로 인식 (다음 처치 때 새 이름으로 바뀜) */
    public static final String N_SOUL_SPLITTER_OLD = "Soul Splitter";
    public static final String N_TEAM_RED = "팀 색상 변경권 (Red)";
    public static final String N_TEAM_GOLD = "팀 색상 변경권 (Gold)";

    /* 컬렉션 아이템 (이름 정확히 일치) */
    public static final String C_CLAYMORE = Msg.plainOf("&6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 클레이모어");
    public static final String C_REAPER_SOUL = Msg.plainOf("&8&l[&7&l✦&8&l] &7&l[Lv.&kjs&7&l] &5&l사신의 영혼");
    public static final String C_JUDGE_HELMET = Msg.plainOf("&6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 헬멧");
    public static final String C_REAPER_HEART = Msg.plainOf("&8&l[&7&l✦&8&l] &7&l[Lv.&kjs&7&l] &c&l사신의 심장");

    private static final java.util.Map<Material, String> KOREAN = java.util.Map.ofEntries(
            java.util.Map.entry(Material.DIAMOND, "다이아몬드"), java.util.Map.entry(Material.GOLD_BLOCK, "금 블록"),
            java.util.Map.entry(Material.EXPERIENCE_BOTTLE, "경험치 병"), java.util.Map.entry(Material.EMERALD_BLOCK, "에메랄드 블록"),
            java.util.Map.entry(Material.GOLDEN_APPLE, "황금 사과"), java.util.Map.entry(Material.NETHERITE_SCRAP, "네더라이트 조각"),
            java.util.Map.entry(Material.ANCIENT_DEBRIS, "고대 잔해"), java.util.Map.entry(Material.TOTEM_OF_UNDYING, "불사의 토템"),
            java.util.Map.entry(Material.ENCHANTED_GOLDEN_APPLE, "마법이 부여된 황금 사과"), java.util.Map.entry(Material.ENCHANTED_BOOK, "마법이 부여된 책"),
            java.util.Map.entry(Material.NETHERITE_INGOT, "네더라이트 주괴"), java.util.Map.entry(Material.IRON_BLOCK, "철 블록"),
            java.util.Map.entry(Material.ENDER_PEARL, "엔더 진주"), java.util.Map.entry(Material.NETHER_STAR, "네더의 별"));

    /** 채팅용 짧은 이름 "이름 x개수" */
    public static String describeShort(ItemStack it) {
        String n;
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            n = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(it.getItemMeta().displayName());
        } else {
            n = "&f" + KOREAN.getOrDefault(it.getType(), it.getType().name().toLowerCase().replace('_', ' '));
        }
        return n + " &7x" + it.getAmount();
    }

    /* ======================= 재료 / 소모품 ======================= */

    public static ItemStack quantumMatter() {
        return It.of(Material.YELLOW_DYE).ench(Enchantment.UNBREAKING, 10)
                .name("<b><gradient:#D4AF37:#FDFBF0><italic:false><b>[Quantum] 양자 물질</b></gradient></b>")
                .lore("%nl%&7서버 스폰의 미스터리 상점에서 사용할 수 있습니다.").build();
    }

    public static ItemStack vibranium() {
        return It.of(Material.ORANGE_DYE).ench(Enchantment.FORTUNE, 10)
                .name("<b><gradient:#8C7427:#FDFBF0><italic:false><b>[Quantum] 압축된 고농도 비브라늄</b></gradient></b>")
                .lore("%nl%&7서버 스폰의 미스터리 상점에서 사용할 수 있습니다.").build();
    }

    public static ItemStack newbiePass() {
        return It.of(Material.PAPER).ench(Enchantment.FORTUNE, 10).name("&a&l[💡 NEWBIE] 뉴비 헬퍼 패스")
                .lore("%nl%&7사용 시 &a뉴비 보호 월드&7에 입장할 수 있으며, 경험치 휙득량이 &a1.5배&7 상승합니다.").build();
    }

    public static ItemStack crownMark() {
        return It.of(Material.NETHER_STAR).ench(Enchantment.FORTUNE, 10)
                .name("<gradient:#FFF176:#FFB732><italic:false><b>[👑] [⭐8]</b><b> 크라</b><b>운 처치 알림 표식</b></gradient>")
                .lore("%nl%&7등급 : <gradient:#FFF176:#FFB732><italic:false><b>Over Lord</b></gradient>%nl%&7오버 로드 ⭐8 달성 보상입니다.%nl%%nl%&e적용 후 플레이어 처치 시 특수한 크라운 표식이 적용됩니다.").build();
    }

    public static ItemStack packHigh(String lore) {
        return It.of(Material.NETHER_STAR).name("&c&l[PVP] &e&l고급 전투 패키지").lore(lore).build();
    }

    public static ItemStack packMid(String lore) {
        return It.of(Material.EMERALD).ench(Enchantment.FORTUNE, 5).name("&c&l[PVP] &6&l중급 전투 패키지").lore(lore).build();
    }

    public static ItemStack melon(int amount) {
        return It.of(Material.MELON_SLICE, amount).ench(Enchantment.DEPTH_STRIDER, 1).name("&d&l최고급 수박 조각")
                .lore("%nl%&7먹으면 <color:#FF7A9C>신속 I</color>&7 8초 · <color:#FF7A9C>재생 III</color>&7 7초 · <color:#FF7A9C>흡수 II</color>&7 2분%nl%&7배고픔이 &f가득&7 찹니다.").build();
    }

    public static ItemStack newbiePackage() {
        return It.of(Material.BOOK).name("&a[Newbie] &7&l뉴비 지원 패키지")
                .lore("%nl%&a우클릭 시 오픈됩니다. 서버를 처음 시작할 때 매우 용이합니다.").build();
    }

    public static ItemStack mineralPack() {
        return It.of(Material.BONE_MEAL).name("&a[Newbie] &7광물 팩")
                .lore("&7다양한 광물이 들어 있습니다!%nl%%nl%&a우클릭 시 오픈됩니다.").build();
    }

    public static final String WEAPON_KEY_LORE = "%nl%&7다양한 무기 룰렛을 오픈할 수 있습니다.%nl%%nl%&7등장하는 아이템 목록%nl%&7- &f다이아몬드 헬멧 (보호 3)%nl%&7- &f다이아몬드 갑옷 (보호 3)%nl%&7- &f다이아몬드 레깅스 (보호 3)%nl%&7- &f다이아몬드 부츠 (보호 3)%nl%&7- &f다이아몬드 검 (날카로뭄 4)%nl%&7- &e마법이 부여된 책 (날카로움 5)%nl%&7- &e마법이 부여된 책 (보호 4)%nl%&7- &e마법이 부여된 책 (내구성 3)%nl%&7- &d삼지창%nl%&7- &d철퇴";
    public static final String HEAVEN_KEY_LORE = "%nl%&c&l천국과 지옥, 과연 어디일까?%nl%%nl%&7등장하는 아이템 목록%nl%&7- &4즉사%nl%&7- &c채굴 피로 10 &7(5분)%nl%&7- &f아무 일도 일어나지 않음%nl%&7- <b><gradient:#D4AF37:#FDFBF0><italic:false><b>[Quantum] 양자 물질</b></gradient></b>%nl%&7- <b><gradient:#4AE3AA:#C7AA49><italic:false><b>[✦] [Special] Fate's Call</b></gradient></b>%nl%%nl%&e해당 룰렛은 야생에서만 사용할 수 있습니다.";

    public static ItemStack weaponKey(int amount, String extraLore) {
        return It.of(Material.PINK_DYE, amount).name("<b><gradient:#67BFE8:#755C6E><b><italic:false>[Roulette] • 다양한 무기 룰렛 키 •</b></gradient></b>")
                .lore(WEAPON_KEY_LORE + (extraLore == null ? "" : extraLore)).build();
    }

    public static ItemStack heavenKey(int amount, String extraLore) {
        return It.of(Material.GRAY_DYE, amount).name("<b><gradient:#F83131:#00FF4F><italic:false><b>[Roulette] • 천국과 지옥 룰렛 키 •</b></gradient></b>")
                .lore(HEAVEN_KEY_LORE + (extraLore == null ? "" : extraLore)).build();
    }

    public static ItemStack sealedKey(int amount, String... extra) {
        return It.of(Material.LIGHT_BLUE_DYE, amount).name("<gradient:#3DE8FA:#FFFFFF><italic:false><bold:true>[Sealed]</gradient> <bold:true><italic:false>• 심해 왕궁 키 •")
                .lore("&7스페셜 상자의 Sealed Master Power 탭에서 오픈할 수 있습니다.").lore(extra).build();
    }

    public static ItemStack ruinerKey(int amount, String... extra) {
        return It.of(Material.RED_DYE, amount).name("<italic:false><b><gradient:#FF0000:#530427><italic:false>[✦] • 파멸의 지배자 키 •</gradient></b>")
                .lore("&7스페셜 상자의 The Ruiner's Gruge 탭에서 오픈할 수 있습니다.").lore(extra).build();
    }

    public static ItemStack timeShard() {
        return It.of(Material.GLOWSTONE_DUST).ench(Enchantment.INFINITY, 1)
                .name("<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] 시간의 파편</b></gradient>")
                .lore("%nl%&7등급 : &dEpic%nl%%nl%&7크로노스의 칼날 업그레이드 재료").build();
    }

    public static final String N_ALTAR_TICKET = "[☀] 천공의 제단 이동권";
    public static final String N_LEGEND_VOUCHER = "[👑] Legendary 무기 교환권";
    public static final String N_MYTHIC_VOUCHER = "[👑] 전설의 무기 교환권";
    /** 예전 이름 (이미 지급된 교환권도 인식) */
    public static final String N_LEGEND_VOUCHER_OLD = "[Over Lord] Legendary 무기 교환권";
    public static final String N_MYTHIC_VOUCHER_OLD = "[Over Lord] 전설의 무기 교환권";

    /**
     * 무기 교환권 (오버 로드 ★5 / ★10). 이름은 그대로 두어 이미 지급된 교환권도 그대로 쓸 수 있음.
     * 설명: 등급 · 한 줄 소개 · 세트별 목록(컬렉션 색) · 사용법
     */
    public static ItemStack legendVoucher() {
        return It.of(Material.PAPER).ench(Enchantment.INFINITY, 1).hide()
                .name("<b><gradient:#29FF00:#EEEEEE><italic:false>[👑] Legendary 무기 교환권</gradient></b>")
                .lore("&7등급 : <gradient:#29FF00:#EEEEEE>Legendary</gradient>",
                        "&8──────────────────",
                        "&7오버 로드의 다섯 번째 별에 새겨진 증표.",
                        "&7지난 시즌의 장비 하나를 골라 되찾을 수 있습니다.",
                        "",
                        "<color:#3DE8FA>❄</color> &fSealed Master Power",
                        "   &7얼어붙은 제왕의 검 &8· &7날개",
                        "<color:#FF5A5A>☠</color> &fThe Ruiner's Grudge",
                        "   &7파괴의 낫 &8· &7재앙의 서막 바지",
                        "<color:#C2C2C2>◎</color> &fWhisper of the Void",
                        "   &7공허의 속삭임 &8· &7뒤틀린 차원의 갑옷",
                        "<color:#6EC6E0>✎</color> &fThe Philosopher's Literature",
                        "   &7아리스토텔레스의 이데아 &8· &7러셀의 패러독스",
                        "&8──────────────────",
                        "&a▶ 우클릭 &7교환할 장비 고르기",
                        "&8교환하면 교환권 1장이 사라집니다.").build();
    }

    public static ItemStack mythicVoucher() {
        return It.of(Material.PAPER).ench(Enchantment.INFINITY, 1).hide()
                .name("<b><gradient:#FFF176:#FFB732><italic:false>[👑] 전설의 무기 교환권</gradient></b>")
                .lore("&7등급 : <b><gradient:#FFF176:#FFB732>OVER LORD</gradient></b>",
                        "&8──────────────────",
                        "&7오버 로드의 마지막 별, 열 번째 별의 보상.",
                        "&7전설로 남은 무기 하나가 당신을 기다립니다.",
                        "",
                        "<color:#8C93FF>🌙</color> &f초승달의 서약 &8(Prologue of Inscribed)",
                        "<color:#FFE14D>⚡</color> &f천공의 심판자 클레이모어 &8(Arbiter of Heaven)",
                        "<color:#7FDBFF>❆</color> &f프로스트 그레이브 &8(Glacier Storm)",
                        "&8──────────────────",
                        "<color:#FFD54A>▶ 우클릭</color> &7교환할 무기 고르기",
                        "&8교환하면 교환권 1장이 사라집니다.").build();
    }

    public static final String N_RANK_TROPHY = "제왕 트로피";

    /**
     * 시즌 순위 TOP 10 트로피 (순위 · 분야 · 시즌 · 주인이 새겨짐, 시즌이 지나도 유지)
     * title: "부의 제왕" · "킬의 제왕", colors: 그라데이션 색 ("#A:#B" 또는 "#A:#B:#C")
     */
    public static ItemStack rankTrophy(String title, String colors, int rank, String owner, String record, String season) {
        Material m = rank == 1 ? Material.GOLD_INGOT : rank == 2 ? Material.IRON_INGOT : rank == 3 ? Material.COPPER_INGOT : Material.AMETHYST_SHARD;
        String medal = kr.wave.core.module.RankingModule.medal(rank);   // 잘리는 메달 이모지 대신
        String tier = rank == 1 ? "<b><gradient:#FFF176:#FFB732>TOP 1</gradient></b>" : rank == 2 ? "<b><gradient:#FFFFFF:#9E9E9E>TOP 2</gradient></b>"
                : rank == 3 ? "<b><gradient:#F0A37B:#B8653F>TOP 3</gradient></b>" : "<b><gradient:#C77DFF:#F5DAF8>Ranking</gradient></b>";
        return It.of(m).ench(Enchantment.INFINITY, 1).hide()
                .name("<b><gradient:" + colors + "><italic:false>[" + medal + "] " + title + " 트로피 · " + rank + "위</gradient></b>")
                .lore("&7등급 : " + tier,
                        "&8──────────────────",
                        "&7" + season + " " + title + " &f" + rank + "위&7의 증표.",
                        "",
                        "&7주인 : &f" + owner,
                        "&7기록 : &f" + record,
                        "&8──────────────────" + SEASON_KEEP).build();
    }

    /** 설정 파일에서 이름으로 부르는 서버 아이템 (상자 보상, 보스 전리품 등). 없으면 null */
    public static ItemStack special(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "nature_boots" -> natureBoots();
            case "time_shard" -> timeShard();
            case "quantum_matter" -> quantumMatter();
            case "vibranium" -> vibranium();
            case "altar_ticket" -> altarTicket();
            case "claymore" -> claymoreSd();
            case "melon" -> melon(1);
            case "chaos_sword", "chaos" -> chaosApocalypse();
            case "darkness_wave" -> darknessWave();
            case "chaos_helmet" -> chaosHelmet();
            default -> null;
        };
    }

    /** 천공의 제단 이동권: 우클릭 또는 /천공의제단 으로 사용 */
    public static ItemStack altarTicket() {
        return It.of(Material.PAPER).ench(Enchantment.INFINITY, 1)
                .name("<b><gradient:#FFF200:#F6E47C:#FFFCD4><italic:false>[☀] 천공의 제단 이동권</gradient></b>")
                .lore("%nl%&7천공의 심판자가 강림한 제단으로 이동합니다.%nl%%nl%&a우클릭 &7하여 천공의 제단 메뉴 열기%nl%&7메뉴에서 &e이동 &7클릭 시 이동%nl%%nl%&c&l1회용 &8(이동 시 1개 소모)%nl%&8심판자가 강림한 동안에만 사용 가능").build();
    }

    public static ItemStack ruinerEssence() {
        return It.of(Material.ENCHANTED_BOOK).ench(Enchantment.FIRE_ASPECT, 3).ench(Enchantment.LOOTING, 4).ench(Enchantment.SWEEPING_EDGE, 5)
                .name("<italic:false><b><gradient:#FF0000:#530427>[✦] 파멸자의 봉인된 정수</gradient></b>")
                .lore(LEGENDARY + "&7우클릭 시 업그레이드 창이 열립니다.").build();
    }

    /** 닉네임 색상 변경권. color: red/gold/purple/magenta/gradient */
    public static ItemStack nickPaper(String color, String source) {
        String name = switch (color) {
            case "gold" -> "&a&l닉네임 색상 변경권 &6&l(Gold)";
            case "purple" -> "&a&l닉네임 색상 변경권 &5&l(Purple)";
            case "magenta" -> "&a&l닉네임 색상 변경권 &d&l(Magenta)";
            case "gradient" -> "&a&l닉네임 색상 변경권 <b><gradient:#FFF176:#FFB732>(Gradient Gold)</gradient></b>";
            case "glacier" -> "&a&l닉네임 색상 변경권 <b><gradient:#39C7FF:#C2FFF5>(Glacier)</gradient></b>";
            default -> "&a&l닉네임 색상 변경권 &c&l(Red)";
        };
        It b = It.of(Material.PAPER).name(name)
                .lore("%nl%&7" + source + "%nl%&7사용 시 채팅, 킬 알림, 탭리스트, 입장 메시지의 닉네임에 특수한 색상이 부여됩니다.%nl%%nl%&a우클릭 시 사용");
        if (color.equals("magenta") || color.equals("gradient") || color.equals("glacier")) b.ench(Enchantment.FORTUNE, 10);
        return b.build();
    }

    public static ItemStack check(long amount, String issuer) {
        return It.of(Material.PAPER).name("§e[🪙] &7캐시 수표")
                .lore("§7금액: §f" + amount + " Cash", "§7발행자: §f" + issuer, " ", "§e&l[!] &7우클릭 시 캐시가 지급됩니다.").build();
    }

    public static ItemStack teamColorBook(String color) {
        String n = color.equals("gold") ? "&a&l팀 색상 변경권 &6&l(Gold)" : "&a&l팀 색상 변경권 &c&l(Red)";
        return It.of(Material.BOOK).name(n).lore("%nl%&7사용할 시 팀 이름에 특수한 색상이 부여됩니다.%nl%%nl%&a우클릭 시 사용").build();
    }

    public static ItemStack supremeRoulette() {
        return It.of(Material.IRON_INGOT).ench(Enchantment.FORTUNE, 1).name("&e[💎] &6Rank Supreme 달성 룰렛")
                .lore("%nl%&e&l랭크 Supreme 달성 보상입니다.%nl%%nl%&7등장하는 아이템 목록:%nl%&d무거운 코어%nl%&d마법이 부여된 황금 사과 x 3%nl%&d겉날개 (내구성 V)%nl%&f거북 등딱지%nl%&f삼지창%nl%&f&e네더의 별%nl%&e크리퍼 머리%nl%&e좀비 머리%nl%&e피글린 머리%nl%%nl%&e&l우클릭 시 오픈됩니다.").build();
    }

    /* ======================= 무기 / 방어구 ======================= */

    public static final String EMBER_NAME = "<b><gradient:#FF3D00:#FF7A18:#FFD36E><italic:false>[Ember] [Lv.✹] 잿불 낙인검</gradient></b>";

    /** 잿불 낙인검: 같은 대상에게 낙인이 4~7개(무작위) 쌓이면 낙인 폭발 (추가 피해 + 화염) */
    public static ItemStack emberBrand(String... extraLore) {
        ItemStack it = It.of(Material.DIAMOND_SWORD).name(EMBER_NAME)
                .lore("%nl%&7등급 : &dEpic%nl%%nl%"
                        + "<b><gradient:#FF7A18:#FFD36E><italic:false>[Lv.Ember 특성]</gradient></b>%nl%"
                        + "&8&l>> &7적중할 때마다 대상에게 &6잿불 낙인&7을 새깁니다 &8(4초 유지)%nl%"
                        + "&8&l>> &7낙인이 &f4~7개&7 모이면 폭발하여 &c추가 피해 2칸&7과 &6화염&7을 입힙니다")
                .lore(extraLore).build();
        var m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    public static final String SHADE_NAME = "<reset><italic:false><b><gradient:#4A6FA5:#9FB4C7:#E8F1F8>[✦] 그림자 사슬</gradient></b>";

    public static final String BELL_NAME = "<reset><italic:false><b><gradient:#FFD86B:#FFF6D5:#8A6BFF>[✦] 메아리 종</gradient></b>";

    /** 메아리 종: 일정 확률로 종을 울려 대상을 어둠과 멀미에 빠뜨리고 밀쳐냄 */
    public static ItemStack echoBell(String... extraLore) {
        return It.of(Material.BELL).ench(Enchantment.UNBREAKING, 10).name(BELL_NAME)
                .lore("%nl%&7등급 : &dEpic%nl%%nl%&7메아리치는 종을 울려 일정 확률로 플레이어의 &9시야와 감각을 빼앗습니다&7.").lore(extraLore).build();
    }

    /** 쇠사슬: 1.21.9 부터 IRON_CHAIN (예전 CHAIN). 서버 버전에 맞춰 찾음 */
    public static Material chainMaterial() {
        Material m = Material.matchMaterial("IRON_CHAIN");
        if (m == null) m = Material.matchMaterial("CHAIN");
        return m != null ? m : Material.IRON_BARS;
    }

    /** 그림자 사슬: 일정 확률로 그림자 사슬을 걸어 적을 끌어당기고 묶음 (시간을 가르는 바람 와 같은 보조 무기) */
    public static ItemStack umbralChain(String... extraLore) {
        return It.of(chainMaterial()).ench(Enchantment.UNBREAKING, 10).name(SHADE_NAME)
                .lore("%nl%&7등급 : &dEpic%nl%%nl%&7그림자 사슬을 던져 일정 확률로 플레이어를 &9끌어당기고 묶어버립니다&7.").lore(extraLore).build();
    }

    public static final String TIME_WIND_NAME = "<reset><italic:false><b><gradient:#00FF7C:#FFAFCA>[✦] 시간을 가르는 바람</gradient></b>";

    public static ItemStack timePiecerWind(String... extraLore) {
        return It.of(Material.BREEZE_ROD).ench(Enchantment.UNBREAKING, 10)
                .name(TIME_WIND_NAME)
                .lore("%nl%&7등급 : &dEpic%nl%%nl%&7시간을 조각하여 일정 확률로 플레이어를 &b하늘로 날려버립니다&7.").lore(extraLore).build();
    }

    /** 망령 수확자: 보라 → 영혼불 청록 */
    public static final String SOUL_SPLITTER_NAME = "<b><gradient:#3A0CA3:#8A5CFF:#5FFBF1><italic:false>[Harvest] [Lv.♱] 망령 수확자</gradient></b>";
    /** 수확 단계: 망령 수, 이름, 얻는 능력 */
    private static final int[] SOUL_STEPS = {50, 100, 250};
    private static final String[] SOUL_STEP_NAME = {"<color:#8A5CFF>첫 번째 각성</color>", "<color:#7FA8FF>두 번째 각성</color>", "<color:#5FFBF1>마지막 각성</color>"};
    private static final String[] SOUL_STEP_TEXT = {"날카로움 IV", "날카로움 V · 약탈 IV", "날카로움 VI · 약탈 V"};

    /**
     * 망령 수확자 고유 번호. 아이템 태그(soul_id) → 예전 커스텀 모델 데이터 순서로 찾고,
     * 둘 다 없으면(상자 보상 등으로 받은 칼) 새 번호를 붙여 태그에 저장한다. meta 는 호출한 쪽에서 setItemMeta 할 것.
     */
    public static int soulId(WaveCore plugin, org.bukkit.inventory.meta.ItemMeta meta) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "soul_id");
        var pdc = meta.getPersistentDataContainer();
        Integer id = pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (id == null && meta.hasCustomModelData()) id = meta.getCustomModelData();
        if (id == null) {
            do id = ThreadLocalRandom.current().nextInt(100000, 1000000);
            while (plugin.store().global().swordKills.containsKey(id));
            plugin.store().global().swordKills.put(id, 0);
        }
        pdc.set(key, org.bukkit.persistence.PersistentDataType.INTEGER, id);
        return id;
    }

    public static boolean isSoulSplitter(ItemStack it) {
        return Msg.nameHas(it, N_SOUL_SPLITTER) || Msg.nameHas(it, N_SOUL_SPLITTER_OLD);
    }

    /** 망령 수확자 설명: 각성 단계 달성 표시, 다음 단계까지 진행 막대 */
    public static String soulSplitterLore(int kills) {
        StringBuilder b = new StringBuilder("%nl%&7등급 : &dEpic%nl%%nl%")
                .append("&7플레이어나 몬스터를 처치할 때마다 망령을 거두어 칼날이 각성합니다.%nl%%nl%")
                .append("<b><gradient:#8A5CFF:#5FFBF1><italic:false>[각성]</gradient></b>%nl%");
        for (int i = 0; i < SOUL_STEPS.length; i++) {
            boolean done = kills >= SOUL_STEPS[i];
            b.append(done ? "&b✦ " : "&8✧ ").append(done ? SOUL_STEP_NAME[i] : "&8" + Msg.plainOf(SOUL_STEP_NAME[i]))
                    .append(done ? " &7(" : " &8(").append(SOUL_STEPS[i]).append(" 망령) ").append(done ? "&f" : "&8").append(SOUL_STEP_TEXT[i]).append("%nl%");
        }
        int next = -1, prev = 0;
        for (int st : SOUL_STEPS) {
            if (kills < st) {
                next = st;
                break;
            }
            prev = st;
        }
        b.append("%nl%&f깃든 망령 : <color:#5FFBF1>").append(kills).append("</color>");
        if (next < 0) {
            b.append(" &8· <gradient:#8A5CFF:#5FFBF1><b>완전 각성</b></gradient>");
        } else {
            b.append(" &7/ ").append(next).append("%nl%");
            int bars = 20, fill = (int) Math.floor((double) (kills - prev) / (next - prev) * bars);
            b.append("<color:#5FFBF1>").append("▮".repeat(Math.max(0, fill))).append("</color>&8").append("▮".repeat(Math.max(0, bars - fill)));
        }
        return b.toString();
    }

    /** 망령 수확자: 고유 번호(커스텀 모델 데이터)로 처치 수를 센다 */
    public static ItemStack soulSplitter(WaveCore plugin, int kills, String... extraLore) {
        int id = ThreadLocalRandom.current().nextInt(100000, 1000000);
        plugin.store().global().swordKills.put(id, kills);
        plugin.store().saveGlobal();
        It b = It.of(Material.NETHERITE_SWORD).cmd(id).name(SOUL_SPLITTER_NAME).lore(soulSplitterLore(kills)).lore(extraLore);
        b.pdcInt(new org.bukkit.NamespacedKey(plugin, "soul_id"), id);
        if (kills >= 250) b.ench(Enchantment.SHARPNESS, 6).ench(Enchantment.LOOTING, 5);
        else if (kills >= 100) b.ench(Enchantment.SHARPNESS, 5).ench(Enchantment.LOOTING, 4);
        else if (kills >= 50) b.ench(Enchantment.SHARPNESS, 4);
        else b.ench(Enchantment.SHARPNESS, 3);
        return b.build();
    }

    /** 망령 수확자 진열용 (고유 번호 없음) */
    public static ItemStack soulSplitterDisplay(String... extraLore) {
        return It.of(Material.NETHERITE_SWORD).ench(Enchantment.SHARPNESS, 3).name(SOUL_SPLITTER_NAME).lore(soulSplitterLore(0)).lore(extraLore).build();
    }

    public static ItemStack voidBow() {
        return It.of(Material.BOW).unbreakable().ench(Enchantment.POWER, 7).ench(Enchantment.FLAME, 1).ench(Enchantment.INFINITY, 1)
                .name("<b><gradient:#F9F9F9:#C2C2C2:#404247><italic:false>[Void] [Lv.✕] 공허의 속삭임</gradient></b>")
                .lore(LEGENDARY + "<b><gradient:#404247:#C2C2C2:#88F4FF><italic:false>심연을 들여다본 자, 이제 그 심연으로 전장을 누빈다.</gradient></b>%nl%%nl%<b><gradient:#404247:#C2C2C2><italic:false>[Lv.Unknown 특성]</gradient></b>%nl%&8&l>> &7무기가 &f부서지지 않습니다%nl%&8&l>> &7적중 시 <color:#88F4FF>구속 II</color>&7를 부여합니다%nl%&8&l>> &7적중 시 일정 확률로 &c데미지의 1.5배&7를 입힙니다" + SEASON_KEEP).build();
    }

    public static ItemStack voidChest() {
        return It.of(Material.NETHERITE_CHESTPLATE).unbreakable().ench(Enchantment.PROTECTION, 5)
                .name("<b><gradient:#F9F9F9:#C2C2C2:#404247><italic:false>[Void] [Lv.✕] 뒤틀린 차원의 갑옷</gradient></b>")
                .lore(LEGENDARY + "<b><gradient:#404247:#C2C2C2:#88F4FF><italic:false>심연을 들여다본 자, 이제 그 심연을 입고 전장을 누빈다.</gradient></b>%nl%%nl%<b><gradient:#404247:#C2C2C2><italic:false>[Lv.Unknown 특성]</gradient></b>%nl%&8&l>> &7방어구가 &f부서지지 않습니다%nl%&8&l>> &7장착 시 <color:#88F4FF>힘 I</color>&7이 부여됩니다%nl%&8&l>> &7공격받을 시 일정 확률로 &e데미지를 반사&7합니다" + SEASON_KEEP).build();
    }

    public static ItemStack russellHelmet() {
        return It.of(Material.NETHERITE_HELMET).unbreakable().ench(Enchantment.PROTECTION, 5).ench(Enchantment.RESPIRATION, 5)
                .name("<b><gradient:#2193b0:#E7F0F2><italic:false>[Philosophy] [Lv.✕] 러셀의 패러독스</gradient></b>")
                .lore(LEGENDARY + "<b><gradient:#2193b0:#E7F0F2><italic:false>넘어짐을 두려워 마라, 그것은 도약을 위한 웅크림이다.</gradient></b>%nl%%nl%<b><gradient:#2193b0:#E7F0F2><italic:false>[Lv.Unknown 특성]</gradient></b>%nl%&8&l>> &7방어구가 &f부서지지 않습니다%nl%&8&l>> &7장착 시 <color:#5CC8E6>성급함 III</color>&7가 부여됩니다%nl%&8&l>> &7일정 확률로 &e받는 데미지를 무효화&7합니다" + SEASON_KEEP).build();
    }

    public static ItemStack aristotlePickaxe() {
        return It.of(Material.NETHERITE_PICKAXE).unbreakable().ench(Enchantment.EFFICIENCY, 7).ench(Enchantment.FORTUNE, 5)
                .name("<b><gradient:#2193b0:#E7F0F2><italic:false>[Philosophy] [Lv.✕] 아리스토텔레스의 이데아</gradient></b>")
                .lore(LEGENDARY + "<b><gradient:#2193b0:#E7F0F2><italic:false>넘어짐을 두려워 마라, 그것은 도약을 위한 웅크림이다.</gradient></b>%nl%%nl%<b><gradient:#2193b0:#E7F0F2><italic:false>[Lv.Unknown 특성]</gradient></b>%nl%&8&l>> &7곡괭이가 &f부서지지 않습니다%nl%&8&l>> &7기본으로 <color:#5CC8E6>효율 VII</color>&7 · <color:#5CC8E6>행운 V</color>&7가 부여되어 있습니다" + SEASON_KEEP).build();
    }

    public static ItemStack quantumSword() {
        return It.of(Material.DIAMOND_SWORD).ench(Enchantment.SHARPNESS, 4)
                .name("<b><gradient:#D4AF37:#FDFBF0><italic:false><b>[Quantum] [Lv.A] 앱솔루트 퀀텀</b></gradient></b>")
                .lore("%nl%&7등급 : &dEpic%nl%%nl%<gradient:#D4AF37:#FDFBF0><italic:false><bold:true>[Lv.Absolute 특성]%nl%&8&l>> &7일정 확률로 타격 시 &c데미지의 1.2배&7를 입힙니다").build();
    }

    public static ItemStack auroraBoots() {
        return It.of(Material.DIAMOND_BOOTS).ench(Enchantment.PROTECTION, 4)
                .name("<b><gradient:#D4AF37:#FDFBF0><italic:false><b>[Quantum] [Lv.A] 오로라 퀸트라늄</b></gradient></b>")
                .lore("%nl%&7등급 : &dEpic%nl%%nl%<gradient:#D4AF37:#FDFBF0><italic:false><bold:true>[Lv.Absolute 특성]%nl%&8&l>> &7일정 확률로 공격받을 시 &e데미지의 일부를 흡수&7합니다").build();
    }

    public static ItemStack chronosBoots() {
        return It.of(Material.DIAMOND_BOOTS).ench(Enchantment.PROTECTION, 4)
                .name("<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] [L</b><b>v.↗</b><b>] 크로노스의 신발</b></gradient> &7(+0)")
                .lore("%nl%&7등급 : &6Upgrade%nl%&7업그레이드 시 특수한 능력이 추가됩니다.%nl%%nl%&7성장 진도 (0/5)%nl%&8&l>> &e보호 5%nl%&8&l>> &e네더라이트화%nl%&8&l>> &c내구도 무한%nl%&8&l>> &4보호 6%nl%&8&l>> <gradient:#BFA66E:#B2D6B6><italic:false><b>모래 폭풍</b></gradient>" + SEASON_KEEP).build();
    }

    public static ItemStack natureBoots() {
        return It.of(Material.DIAMOND_BOOTS).unbreakable().ench(Enchantment.PROTECTION, 5).ench(Enchantment.FEATHER_FALLING, 5).ench(Enchantment.DEPTH_STRIDER, 5)
                .name("<gradient:#F5E400:#95FFBE><italic:false><b>[Natural]</b><b> [Lv.✦</b><b>] 대자연의 황혼</b></gradient>")
                .lore("%nl%&7등급 : <gradient:#F5E400:#95FFBE><italic:false><b>The Echoe</b><b>s o</b><b>f Nature</b></gradient>%nl%&7스페셜 테마 뽑기에서만 휙득할 수 있는 아이템입니다.%nl%%nl%<gradient:#F5E400:#95FFBE><italic:false><b>[드루이드의 축복]</b></gradient>%nl%&e&l>> &7방어구가 &f부서지지 않습니다%nl%&e&l>> &7장착 시 <color:#95FFBE>신속 I</color>&7이 부여됩니다%nl%&e&l>> &7장착 시 &e배고픔이 감소하지 않습니다%nl%&e&l>> &7숲 바이옴에 있을 시 <color:#95FFBE>재생 II</color>&7를 부여받습니다" + SEASON_KEEP).build();
    }

    public static ItemStack ruinerScythe() {
        return It.of(Material.NETHERITE_HOE).unbreakable().ench(Enchantment.INFINITY, 1)
                .name("<italic:false><b><gradient:#FF0000:#530427>[Ruiner] [Lv.✕] 파괴의 낫</gradient></b>")
                .lore(LEGENDARY + "<italic:false><b><gradient:#FF0000:#551010>무엇을 베어내든 결국 부서진 파편만이 남으리라.</gradient></b>%nl%%nl%<gradient:#FF0000:#DC051F><italic:false><bold:true>[Lv.Unknown 특성]</gradient>%nl%&8&l>> &7무기 기본 데미지가 &c6&7으로 고정됩니다%nl%&8&l>> &7타격 시 <color:#E0464E>구속 I</color>&7을 부여합니다%nl%&8&l>> &7일정 확률로 &c적의 체력을 흡수&7합니다" + SEASON_KEEP).build();
    }

    public static ItemStack ruinerPants() {
        return It.of(Material.NETHERITE_LEGGINGS).unbreakable().ench(Enchantment.PROTECTION, 6)
                .name("<italic:false><b><gradient:#FF0000:#530427>[Ruiner] [Lv.✕] 재앙의 서막 바지</gradient></b>")
                .lore(LEGENDARY + "<italic:false><b><gradient:#FF0000:#551010>무엇을 베어내든 결국 부서진 파편만이 남으리라.</gradient></b>%nl%%nl%<gradient:#FF0000:#DC051F><italic:false><bold:true>[Lv.Unknown 특성]</gradient>%nl%&8&l>> &7방어구가 &f부서지지 않습니다%nl%&8&l>> &7장착 시 &e낙하 피해&7와 &e화염 피해&7가 &f50%&7 감소합니다%nl%&8&l>> &7체력이 &f30% 이하&7일 때 <color:#E0464E>힘 II</color>&7가 부여됩니다%nl%&8&l>> &7일정 확률로 &e죽음을 거부&7하고 체력의 &a절반&7으로 되돌아옵니다" + SEASON_KEEP).build();
    }

    public static ItemStack sealedWings() {
        return It.of(Material.ELYTRA).unbreakable().ench(Enchantment.UNBREAKING, 10)
                .name("<gradient:#00B3FF:#DFDFDF><italic:false><bold:true>[Sealed]</gradient> <gradient:#D1D1D1:#DBDBDB><italic:false><bold:true>[Lv.✕]</gradient> <gradient:#DFDFDF:#919191><italic:false><bold:true>얼어붙은 제왕의 날개</gradient>")
                .lore(LEGENDARY + "<gradient:#00DDFF:#FFD8D8><italic:false><bold:true>얼어붙은 고대 제왕의 마지막 전리품%nl%%nl%<gradient:#00DDFF:#FFD8D8><gradient:#00DDFF:#FFFFFF><italic:false><bold:true>[Lv.Unknown 특성]</gradient>%nl%&8&l>> &7겉날개가 &f부서지지 않습니다" + SEASON_KEEP).build();
    }

    public static ItemStack sealedSword() {
        return It.of(Material.DIAMOND_SWORD).unbreakable().ench(Enchantment.SHARPNESS, 6)
                .name("<gradient:#00B3FF:#DFDFDF><italic:false><bold:true>[Sealed]</gradient> <gradient:#D1D1D1:#DBDBDB><italic:false><bold:true>[Lv.✕]</gradient> <gradient:#DFDFDF:#919191><italic:false><bold:true>얼어붙은 제왕의 검</gradient>")
                .lore(LEGENDARY + "<gradient:#00DDFF:#FFD8D8><italic:false><bold:true>얼어붙은 고대 제왕의 마지막 전리품</gradient>%nl%%nl%<gradient:#00DDFF:#FFD8D8><gradient:#00DDFF:#FFFFFF><italic:false><bold:true>[Lv.Unknown 특성]</gradient>%nl%&8&l>> &7무기가 &f부서지지 않습니다%nl%&8&l>> &7무기를 들고 있으면 <color:#00DDFF>저항 I</color>&7이 부여됩니다%nl%&8&l>> &7타격 시 일정 확률로 &c데미지의 1.3배&7를 입힙니다%nl%&8&l>> &7우클릭 시 &f6초&7간 타격이 주변 적에게 튀어 &c피해 1칸 &8(쿨타임 40초)" + SEASON_KEEP).build();
    }

    public static ItemStack thunderAxe() {
        return It.of(Material.IRON_AXE).unbreakable().ench(Enchantment.CHANNELING, 5)
                .name("<gradient:#C2D93F:#AFEEF8><italic:false><b>[⚡] [Lv.L</b><b>] 천</b><b>둥의 심판</b></gradient>")
                .lore("%nl%&7등급 : &dEpic%nl%%nl%<gradient:#C2D93F:#AFEEF8><italic:false><b>[Lv.Light</b><b>nin</b><b>g 특성]</b></gradient>%nl%&8&l>> &7타격 시 일정 확률로 &e번개&7를 소환합니다 &c(고정 피해 2칸)").build();
    }

    private static final String CHAOS_HEAD = "%nl%&7등급 : <gradient:#1F1F1F:#BCBCBC><italic:false><b>Catastrop</b><b>he</b></gradient><gradient:#BCBCBC:#727272><italic:false><b> </b><b>of Chaos</b></gradient>%nl%&7스페셜 테마 상자에서만 휙득할 수 있는 아이템입니다.%nl%%nl%<gradient:#1F1F1F:#BCBCBC><italic:false><b>[다크니스의 재앙</b><b>]</b></gradient>%nl%";

    /** 다크니스 웨이브 (혼돈의 아포칼립스 세트 스킬 아이템, 초승달의 서약과 같은 형태) */
    public static ItemStack darknessWave() {
        return It.of(Material.MUSIC_DISC_11).ench(Enchantment.UNBREAKING, 10)
                .name("<gradient:#1F1F1F:#BCBCBC><italic:false><b>[Chaos] [Lv.✦] </b></gradient><gradient:#BCBCBC:#727272><italic:false><b>다크니스 웨이브</b></gradient>")
                .lore(CHAOS_HEAD + "&8&l>> &7우클릭 시 주변 &f6칸&7의 적에게 <color:#D6D6D6>어둠</color>&7 4초 + <color:#D6D6D6>구속 III</color>&7 3초, 밀쳐냅니다 &8(쿨타임 1분)%nl%"
                        + "&8&l>> &7웅크리기 + 우클릭 시 &f5초&7간 주변 &f3칸&7에 검은 연기가 퍼져, 들어온 적에게 <color:#D6D6D6>실명</color>&7을 부여합니다 &8(쿨타임 1분 30초)" + SEASON_KEEP).build();
    }

    /** 종말의 투구 (혼돈의 아포칼립스 세트 헬멧) */
    public static ItemStack chaosHelmet() {
        return It.of(Material.NETHERITE_HELMET).unbreakable().ench(Enchantment.PROTECTION, 5)
                .name("<gradient:#1F1F1F:#BCBCBC:#727272><italic:false><b>[Chaos] [Lv.✦] 종말의 투구</b></gradient>")
                .lore(CHAOS_HEAD + "&8&l>> &7방어구가 &f부서지지 않습니다%nl%"
                        + "&8&l>> &7장착 시 <color:#D6D6D6>나약함</color>&7과 <color:#D6D6D6>구속</color>&7에 걸리지 않습니다%nl%"
                        + "&8&l>> &7공격받을 시 일정 확률로 공격자에게 <color:#D6D6D6>위더 I</color>&7를 부여합니다%nl%"
                        + "&8&l>> &7체력이 &f30% 미만&7일 때 &e받는 데미지&7가 &f20%&7 감소합니다"
                        + SEASON_KEEP).build();
    }

    public static ItemStack chaosApocalypse() {
        return It.of(Material.NETHERITE_HOE).unbreakable().ench(Enchantment.INFINITY, 1)
                .name("<gradient:#1F1F1F:#BCBCBC:#727272><italic:false><b>[Chaos] [</b><b>Lv.</b><b>✦] 혼돈의 아포칼립스</b></gradient>")
                .lore("%nl%&7등급 : <gradient:#1F1F1F:#BCBCBC><italic:false><b>Catastrop</b><b>he</b></gradient><gradient:#BCBCBC:#727272><italic:false><b> </b><b>of Chaos</b></gradient>%nl%&7스페셜 테마 상자에서만 휙득할 수 있는 아이템입니다.%nl%%nl%<gradient:#1F1F1F:#BCBCBC><italic:false><b>[다크니스의 재앙</b><b>]</b></gradient>%nl%&8&l>> &7무기 기본 데미지가 &c6.5&7로 고정됩니다%nl%&8&l>> &7타격 시 <color:#D6D6D6>구속 II</color>&7를 부여합니다%nl%&8&l>> &7타격 시 일정 확률로 &c적의 체력을 절반&7으로 만듭니다%nl%&8&l>> &7우클릭 시 주변 &f5칸&7의 적에게 &c피해 2칸&7 + <color:#D6D6D6>위더 II</color>&7 3초, 밀쳐냅니다 &8(쿨타임 45초)" + SEASON_KEEP).build();
    }

    public static final String CLAYMORE_SD_NAME = "&6&l[&a&l⚡&6&l] <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[Lv.</b><obf><b>sd</b></obf><b>] 천</b><b>공의 </b><b>심판자 클레이모어</b></gradient>";
    public static final String CLAYMORE_SD_LORE = "%nl%&7등급 : <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>Arbiter o</b><b>f H</b><b>eaven</b></gradient>%nl%&7S4 시즌에서만 휙득할수 있으며, 시즌이 지나도 아이템이 사라지지 않습니다.%nl%%nl%<gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[심판자의</b><obf><b> </b></obf><b>권한]</b></gradient>%nl%&e&l>> &7무기 소지 시 <color:#FFE94D>이동 속도</color> 증가%nl%&e&l>> &7무기 소지 시 &a최대 체력 3칸 증가%nl%&e&l>> &7플레이어 타격 시 &c강력한 방어구 무시 피해&7를 입힙니다%nl%%nl%&c&l드롭 방지가 적용되며, 사망해도 아이템이 드롭되지 않습니다.";

    public static ItemStack claymoreSd() {
        return It.of(Material.GOLDEN_SWORD).unbreakable().ench(Enchantment.CHANNELING, 10).name(CLAYMORE_SD_NAME).lore(CLAYMORE_SD_LORE).build();
    }

    public static final String FROST_NAME = "<gradient:#39C7FF:#D2FFF8:#00FFFF><italic:false><b>[❆] [Lv.</b><obf><b>D</b></obf><obf><b>D</b></obf><b>] </b><b>프로스트 그레이브</b></gradient>";
    public static final String FROST_LORE = "%nl%&7등급 : <gradient:#39C7FF:#C2FFF5:#F5DAF8><italic:false><b>Glacier S</b><b>tor</b><b>m</b></gradient>%nl%&7S6 시즌에서만 휙득할수 있으며, 시즌이 지나도 아이템이 사라지지 않습니다.%nl%%nl%<gradient:#39C7FF:#C2FFF5><italic:false><b>[아이스 블래스트</b><b>]</b></gradient>%nl%<color:#39C7FF><b>>></b><gray> 무기 소지 시 <green>최대 체력 4칸 증가</green></gray></color>%nl%<color:#39C7FF><b>>></b><gray> 무기 소지 시 <yellow>받는 데미지 감소</yellow> <dark_gray>(0.8x)</dark_gray></gray>%nl%<color:#39C7FF><b>>></b><gray> 타격 시 <color:#6FD6FF>구속 II</color>, <color:#6FD6FF>나약함 I</color>를 부여합니다</gray></color>%nl%<color:#39C7FF><b>>></b><gray> 엔티티 타격 시 일정 확률로 <red>즉시 처치</red> 발동</gray></color>%nl%%nl%&c&l드롭 방지가 적용되며, 사망해도 아이템이 드롭되지 않습니다.";

    public static ItemStack frostGrave() {
        return It.of(Material.DIAMOND_SWORD).unbreakable().ench(Enchantment.SHARPNESS, 7).name(FROST_NAME).lore(FROST_LORE).build();
    }

    /** 초승달의 서약 (예전 The New Characteristic 과 같은 색) */
    public static final String NEW_CHAR_NAME = "<gradient:#1E2586:#C8B0B0><italic:false><b>[🌙] [Lv.</b><obf><b></b></obf><obf><b>DD</b></obf><b>]</b><b> 초승달의</b></gradient><gradient:#FAD4D4:#C8B0B0><italic:false><b> 서약</b></gradient>";
    public static final String NEW_CHAR_LORE = "%nl%&7등급 : <gradient:#1E2586:#FAD4D4><italic:false><b>INSCRIBE</b></gradient><gradient:#FAD4D4:#FAD4D4><italic:false><b>D</b></gradient>%nl%&7S4 시즌에서만 휙득할수 있으며, 시즌이 지나도 아이템이 사라지지 않습니다.%nl%%nl%<gradient:#1E2586:#00FBFF><italic:false><b>[초승달의 </b><b>각인</b></gradient><gradient:#00FBFF:#00FBFF><italic:false><b>]</b></gradient>%nl%<color:#313798><b>>></b></color> &7우클릭 시 <color:#F49AC1>힘 III</color>&7를 &f20초&7간 부여받습니다 &8(쿨타임 1분 30초)%nl%<color:#313798><b>>></b></color> &7웅크리기 + 우클릭 시 <color:#F49AC1>재생 V</color>&7를 &f6초&7간 부여받습니다 &8(쿨타임 1분 30초)%nl%%nl%&c&l드롭 방지가 적용되며, 사망해도 아이템이 드롭되지 않습니다.";

    public static ItemStack newCharacteristic() {
        return It.of(Material.MUSIC_DISC_5).ench(Enchantment.UNBREAKING, 10).name(NEW_CHAR_NAME).lore(NEW_CHAR_LORE).build();
    }

    /* ---------------- 크로노스의 칼날 (0~6단계) ---------------- */

    private static final String CHRONO_NAME = "<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] [L</b><b>v.↗</b><b>] 크로노스의 칼날</b></gradient>";
    private static final String[] CHRONO_SUFFIX = {" &7(+0)", " &f(+1)", " &e(+2)", " &6(+3)", " &c(+4)", " &4(+5)"};
    private static final String[] CHRONO_PROGRESS = {"&7성장 진도 (0/6)", "&7성장 진도 &f(1/6)", "&7성장 진도 &e(2/6)", "&7성장 진도 &6(3/6)", "&7성장 진도 &c(4/6)", "&7성장 진도 &4(5/6)"};
    private static final String[][] CHRONO_LINES = {
            {"&8&l>> &7날카로움 5", "&8&l>> &7네더라이트화", "&8&l>> &7타격 이펙트 &7(나약함 I)", "&8&l>> &7날카로움 6&l", "&8&l>> &7타격 이펙트 &7(확률적으로 추가 데미지 1.25x)"},
            {"&a&l>> &f날카로움 5", "&8&l>> &7네더라이트화", "&8&l>> &7타격 이펙트 &7(나약함 I)", "&8&l>> &7날카로움 6&l", "&8&l>> &7타격 이펙트 &7(확률적으로 추가 데미지 1.25x)"},
            {"&a&l>> &f날카로움 5", "&a&l>> &e네더라이트화", "&8&l>> &7타격 이펙트 &7(나약함 I)", "&8&l>> &7날카로움 6&l", "&8&l>> &7타격 이펙트 &7(확률적으로 추가 데미지 1.25x)"},
            {"&a&l>> &f날카로움 5", "&a&l>> &e네더라이트화", "&a&l>> &6타격 이펙트 &7(<color:#B2D6B6>나약함 I</color>&7)", "&8&l>> &7날카로움 6&l", "&8&l>> &7타격 이펙트 &7(확률적으로 추가 데미지 1.25x)"},
            {"&a&l>> &f날카로움 5", "&a&l>> &e네더라이트화", "&a&l>> &6타격 이펙트 &7(<color:#B2D6B6>나약함 I</color>&7)", "&a&l>> &c날카로움 6", "&8&l>> &7타격 이펙트 &7(확률적으로 추가 데미지 1.25x)"},
            {"&a&l>> &f날카로움 5", "&a&l>> &e네더라이트화", "&a&l>> &6타격 이펙트 &7(<color:#B2D6B6>나약함 I</color>&7)", "&a&l>> &c날카로움 6", "&a&l>> &4타격 이펙트 &7(확률적으로 &c추가 데미지 1.25x&7)"},
    };

    /** 0~5 단계 크로노스의 칼날, 6 은 이터널 크로노스 */
    public static ItemStack chronos(int stage, String... extraLore) {
        if (stage >= 6) {
            return It.of(Material.NETHERITE_SWORD).unbreakable().ench(Enchantment.SHARPNESS, 6)
                    .name("<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] [L</b><b>v.↗</b><b>] 이터널 크로노스</b></gradient> <color:#CBC1A9><b>(MAX)</b></color>")
                    .lore("%nl%&7등급 : <gradient:#BFA66E:#B2D6B6><italic:false><b>Eternaled</b></gradient>%nl%&7크로노스의 칼날 최대 레벨 등급%nl%%nl%&7성장 진도 <color:#CBC1A9><b>(6/6)</b></color>%nl%&a&l>> &f날카로움 5%nl%&a&l>> &e네더라이트화%nl%&a&l>> &6타격 이펙트 &7(<color:#B2D6B6>나약함 I</color>&7)%nl%&a&l>> &c날카로움 6%nl%&a&l>> &4타격 이펙트 &7(확률적으로 &c추가 데미지 1.25x&7)%nl%&a&l>> <gradient:#BFA66E:#B2D6B6><italic:false><b>모래 폭풍</b></gradient>" + SEASON_KEEP)
                    .lore(extraLore).build();
        }
        Material m = stage >= 2 ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD;
        int sharp = stage == 0 ? 4 : stage <= 3 ? 5 : 6;
        StringBuilder lore = new StringBuilder("%nl%&7등급 : &6Upgrade%nl%&7업그레이드 시 특수한 능력이 추가됩니다.%nl%%nl%");
        lore.append(CHRONO_PROGRESS[stage]);
        for (String l : CHRONO_LINES[stage]) lore.append("%nl%").append(l);
        lore.append("%nl%&8&l>> &7???");
        if (stage >= 4) lore.append(SEASON_KEEP);
        return It.of(m).unbreakable().ench(Enchantment.SHARPNESS, sharp).name(CHRONO_NAME + CHRONO_SUFFIX[stage]).lore(lore.toString()).lore(extraLore).build();
    }

    /** 크로노스의 칼날 단계 판별 (-1 = 아님) */
    public static int chronosStage(ItemStack it) {
        String n = Msg.name(it);
        if (n.contains(N_ETERNAL_CHRONOS)) return 6;
        if (!n.contains(N_CHRONOS)) return -1;
        for (int i = 0; i <= 5; i++) if (n.endsWith("(+" + i + ")")) return i;
        return -1;
    }

    /* ---------------- 프리오더 ---------------- */

    public static ItemStack judgeHelmet() {
        return It.of(Material.NETHERITE_HELMET).unbreakable().ench(Enchantment.PROTECTION, 5)
                .name("&6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 헬멧")
                .lore("%nl%&c&l-심판의 빛이 머리 위를 스친다.%nl%%nl%&7&lS2 시즌에서만 휙득할수 있으며, 시즌이 지나도 아이템이 사라지지 않습니다.%nl%&a&l%nl%&6&l[심판자의 권한]%nl%&a&l- &7헬멧 장착 시 &e받는 데미지 감소%nl%&a&l- &7데미지를 받을 시 일정 확률로 &a체력을 회복&7합니다%nl%&a&l- &7헬멧이 &f부서지지 않습니다%nl%%nl%&c&l드롭 방지가 적용되며, 사망해도 아이템이 드롭되지 않습니다.").build();
    }

    public static ItemStack claymoreJs() {
        return It.of(Material.GOLDEN_SWORD).unbreakable().ench(Enchantment.CHANNELING, 10)
                .name("&6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 클레이모어")
                .lore("%nl%&c&l-심판의 빛이 머리 위를 스친다.%nl%%nl%&7&lS2 시즌에서만 휙득할수 있으며, 시즌이 지나도 아이템이 사라지지 않습니다.%nl%%nl%&a&l&6&l[심판자의 권한]%nl%&a&l- &7무기 장착 시 <color:#FFE94D>이동 속도</color> 증가%nl%&a&l- &7플레이어 타격 시 &c강력한 고정 피해&7를 입힙니다%nl%&a&l- &7최대 체력 &a3칸 증가%nl%%nl%&c&l드롭 방지가 적용되며, 사망해도 아이템이 드롭되지 않습니다.").build();
    }

    public static ItemStack preorderTicket() {
        return It.of(Material.AMETHYST_SHARD).ench(Enchantment.FORTUNE, 10).name("&d&l[Pre-Order] &b&l행운 티켓")
                .lore("%nl%&a&l운에 맡겨보세요!%nl%%nl%&7등장하는 아이템 목록:%nl%&7- &6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 헬멧%nl%&7- &6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 클레이모어%nl%&7- &d&l[Pre-Order] &bStarter Pickaxe%nl%&7- &f네더라이트 주괴 x 8%nl%&7- &f다이아몬드 x 40%nl%&7- &f엔더의 눈 x 20%nl%&7- &a[Newbie] &7&l뉴비 지원 패키지").build();
    }

    /* ---------------- 칭호 책 ---------------- */

    /** 컬렉션 칭호: {키, 표시 이름(MiniMessage), LuckPerms 접두사} */
    public static final String[][] COLLECTION_TITLES = {
            {"sealed", "<b><gradient:#00E7FF:#FFFFFF>[✦] 얼어붙은 제왕</gradient></b>", "\"<b><gradient:#00E7FF:#FFFFFF>[✦] 얼어붙은 제왕</gradient></b>\""},
            {"destroy", "<b><gradient:#FF0000:#530427>[✦] 파멸의 지배자</gradient></b>", "\"<b><gradient:#FF0000:#530427>[✦] 파멸의 지배자</gradient></b>\""},
            {"void", "<b><gradient:#C2C2C2:#404247>[✦] 공허의 방랑자</gradient></b>", "\"<b><gradient:#C2C2C2:#404247>[✦] 공허의 방랑자</gradient></b>\""},
            {"philosopher", "<b><gradient:#2193b0:#E7F0F2>[✦] 사유의 현자</gradient></b>", "\"<b><gradient:#2193b0:#E7F0F2>[✦] 사유의 현자</gradient></b>\""},
            {"inscribed", "<b><gradient:#1E2586:#FAD4D4>[🌙] 초승달의 서약자</gradient></b>", "\"<b><gradient:#1E2586:#FAD4D4>[🌙] 초승달의 서약자</gradient></b>\""},
            {"arbiter", "<b><gradient:#FFF200:#FFFCD4>[⚡] 천공의 심판자</gradient></b>", "\"<b><gradient:#FFF200:#FFFCD4>[⚡] 천공의 심판자</gradient></b>\""},
            {"glacier", "<b><gradient:#39C7FF:#C2FFF5>[❆] 빙하의 군주</gradient></b>", "\"<b><gradient:#39C7FF:#C2FFF5>[❆] 빙하의 군주</gradient></b>\""},
    };

    /** 이름이 바뀐 칭호: 적용 중인 플레이어는 접속 시 새 접두사로 다시 적용. 바뀐 게 아니면 null */
    public static String renamedTitle(String key) {
        if (!"inscribed".equals(key)) return null;
        for (String[] t : COLLECTION_TITLES) if (t[0].equals(key)) return t[2];
        return null;
    }

    /** 컬렉션 보상 칭호북 (우클릭 시 칭호 획득 + 적용) */
    public static ItemStack collectionTitleBook(String key, String collectionName) {
        for (String[] t : COLLECTION_TITLES) {
            if (!t[0].equals(key)) continue;
            return It.of(Material.BOOK).ench(Enchantment.UNBREAKING, 1).hide().name("&7[칭호] " + t[1].replace("<b>", "<b><italic:false>"))
                    .lore("%nl%&7획득 방법 :%nl%&7" + collectionName + " 컬렉션의 모든 아이템을 수집하세요.%nl%%nl%&7우클릭 시 적용됩니다").build();
        }
        return null;
    }

    public static ItemStack[] titleBooks() {
        return new ItemStack[]{
                It.of(Material.BOOK).name("&7[칭호] &e[Ace]").lore("&7휙득 방법 : 랭크 &e[Ace] &7를 달성하기%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] &a[💎] &bSupreme").lore("&7휙득 방법 : 랭크 &a[💎] &bSupreme &7을 달성하기%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] &a[💎] &6Exceed").lore("&7휙득 방법 : 랭크 &a[💎] &6Exceed &7를 달성하기%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] &4[💎] &cPeerless").lore("&7휙득 방법 : 랭크 &4[💎] &cPeerless &7를 달성하기%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] <b><gradient:#00E7FF:#FFFFFF><italic:false>[✦] 얼어붙은 제왕</gradient></b>").lore("&7휙득 방법 :%nl%&7Sealed Master Power 컬렉션의 모든 아이템을 수집하세요.%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] <b><gradient:#FF0000:#530427><italic:false>[✦] 파멸의 지배자</gradient></b>").lore("&7휙득 방법 :%nl%&7The Ruiner's Grudge 컬렉션의 모든 아이템을 수집하세요.%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] <b><gradient:#670B0B:#FD4141><italic:false><b>[✧] 네더 로드</b></gradient></b>").lore("%nl%&e&lS1 시즌 한정 칭호입니다.%nl%%nl%&7휙득 방법 :%nl%&7스폰의 미스터리 상점에서 교환할 수 있습니다.%nl%%nl%&7우클릭 시 적용됩니다").build(),
                It.of(Material.BOOK).name("&7[칭호] &e&l[💦] 파수꾼").lore("%nl%&7휙득 방법 :%nl%&77일 연속 출석하기%nl%%nl%&7우클릭 시 적용됩니다").build(),
        };
    }
}
