package com.example.gallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppConstants {
    // 背景色
    val BackgroundColor = Color.DarkGray

    // レイアウト
    val HeaderHeight = 56.dp

    // フォントサイズ
    val TitleFontSize = 24.sp
    val HeaderFontSize = 20.sp
    val BodyFontSize = 16.sp
    val SubtitleFontSize = 14.sp
    val SmallFontSize = 12.sp

    // AIタグ翻訳 (SmilingWolf WD Tagger v3 用の主要タグ)
    val TagTranslationMap = mapOf(
        "1girl" to "女の子",
        "1boy" to "男の子",
        "solo" to "単体",
        "smile" to "笑顔",
        "long_hair" to "ロングヘア",
        "short_hair" to "ショートヘア",
        "medium_hair" to "ミディアムヘア",
        "black_hair" to "黒髪",
        "blonde_hair" to "金髪",
        "brown_hair" to "茶髪",
        "silver_hair" to "銀髪",
        "white_hair" to "白髪",
        "blue_hair" to "青髪",
        "pink_hair" to "桃髪",
        "purple_hair" to "紫髪",
        "red_hair" to "赤髪",
        "green_hair" to "緑髪",
        "orange_hair" to "オレンジ髪",
        "ponytail" to "ポニーテール",
        "twintails" to "ツインテール",
        "bob_cut" to "ボブカット",
        "hair_ornament" to "髪飾り",
        "blue_eyes" to "青い目",
        "brown_eyes" to "茶色の目",
        "red_eyes" to "赤い目",
        "green_eyes" to "緑の目",
        "yellow_eyes" to "黄色い目",
        "purple_eyes" to "紫の目",
        "looking_at_viewer" to "目線あり",
        "blush" to "照れ",
        "outdoors" to "屋外",
        "indoors" to "屋内",
        "sky" to "空",
        "mountain" to "山",
        "sea" to "海",
        "beach" to "ビーチ",
        "forest" to "森",
        "tree" to "木",
        "grass" to "草",
        "flower" to "花",
        "street" to "街路",
        "cityscape" to "都市",
        "building" to "建物",
        "night" to "夜",
        "sunlight" to "日光",
        "clouds" to "雲",
        "scenery" to "風景",
        "background" to "背景",
        "water" to "水",
        "sunset" to "夕焼け",
        "animal" to "動物",
        "cat" to "猫",
        "dog" to "犬",
        "bird" to "鳥",
        "food" to "食べ物",
        "fruit" to "果物",
        "cake" to "ケーキ",
        "bread" to "パン",
        "shirt" to "シャツ",
        "skirt" to "スカート",
        "dress" to "ドレス",
        "glasses" to "眼鏡",
        "hat" to "帽子",
        "jewelry" to "宝石",
        "swimsuit" to "水着",
        "underwear" to "下着",
        "bikini" to "ビキニ",
        "school_uniform" to "学生服",
        "serafuku" to "セーラー服",
        "ribbon" to "リボン",
        "socks" to "靴下",
        "shoes" to "靴",
        "gloves" to "手袋",
        "thighhighs" to "ニーソックス"
    )

    // センシティブなワード (これらが含まれていたら自動的に R15/R18)
    val R15Keywords = setOf("underwear", "lingerie", "cleavage", "panties", "bra", "swimsuit", "bikini")
    val R18Keywords = setOf("nude", "naked", "sex", "hentai", "vagina", "penis", "pussy", "fellatio", "masturbation")
}
