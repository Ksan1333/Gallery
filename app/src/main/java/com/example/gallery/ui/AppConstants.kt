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

    // センシティブなワード (これらが含まれていたら自動的に R15/R18)
    val R15Keywords = setOf(
        "cleavage", "underboob", "sideboob", "bikini", "swimsuit", "one-piece_swimsuit",
        "school_swimsuit", "competition_swimsuit", "micro_bikini", "slingshot_swimsuit",
        "highleg", "highleg_swimsuit", "thong", "lingerie", "bra", "panties", "no_bra",
        "no_panties", "panties_aside", "panty_pull", "panty_lift", "skirt_lift", "dress_lift",
        "clothes_lift", "shirt_lift", "bra_lift", "sports_bra_lift", "bikini_lift", "pantyshot",
        "upskirt", "cameltoe", "cleft_of_venus", "wet", "see-through", "wet_clothes", "wet_shirt",
        "wet_panties", "wet_swimsuit", "transparent", "see-through_swimsuit", "fishnet", "fishnets",
        "fishnet_pantyhose", "garter_belt", "garter_straps", "thigh_strap", "thong_aside",
        "string_panties", "micro_panties", "micro_bra", "pasties", "nipple_slip", "breast_slip",
        "wardrobe_malfunction", "nipple_cutout", "cleavage_cutout", "clothing_cutout",
        "backless_outfit", "side_slit", "lowleg", "lowleg_skirt", "lowleg_panties", "off_shoulder",
        "bare_shoulders", "bare_back", "midriff", "navel", "underwear_only", "bikini_top_only",
        "bikini_bottom_only", "strapless", "strap_slip", "strap_gap", "open_shirt", "open_jacket",
        "open_coat", "open_robe", "clothes_pull", "sweater_pull", "hoodie_lift", "apron_lift",
        "skirt_hold", "breast_hold", "grabbing_own_breast", "hand_on_own_breast", "breast_poke",
        "breast_grab", "ass_grab", "ass_focus", "hip_focus", "thighs", "thick_thighs",
        "zettai_ryouiki", "thigh_gap", "moles", "mole_under_eye", "mole_under_mouth",
        "mole_on_breast", "tanlines", "tan", "dark_skin", "shiny_skin", "sweat", "sweatdrop",
        "blush", "full-face_blush", "ear_blush", "nose_blush", "heart-shaped_pupils", "heart_eyes",
        "tongue_out", "licking_lips", "parted_lips", "open_mouth", "ahegao"  // 軽めのみ
    )
    val R18Keywords = setOf(
        "nipples", "pussy", "penis", "nude", "sex", "vaginal", "cum", "cum_in_pussy",
        "cum_in_mouth", "cum_on_body", "cum_on_hair", "cumdrip", "ejaculation", "internal_cumshot",
        "creampie", "after_sex", "ahegao", "orgasm", "female_orgasm", "forced_orgasm",
        "masturbation", "fellatio", "handjob", "paizuri", "footjob", "anal", "double_penetration",
        "triple_penetration", "group_sex", "threesome", "gangbang", "bukkake", "futanari",
        "futasub", "futanari_on_futanari", "tentacle_sex", "tentacles", "object_insertion",
        "dildo", "double_dildo", "vibrator", "sex_toy", "egg_vibrator", "nipple_tweak",
        "nipple_pull", "breast_poke", "grabbing_another's_breast", "breast_hold", "breast_press",
        "paizuri_invitation", "implied_fellatio", "implied_fingering", "cunnilingus", "anilingus",
        "facesitting", "facesit", "69", "missionary", "doggystyle", "cowgirl_position",
        "reverse_cowgirl_position", "spooning", "standing_sex", "sex_from_behind", "straddling",
        "lap_pillow_invitation", "rape", "mind_control", "drugged", "sleeping_sex", "somnophilia",
        "public_use", "glory_wall", "urinal", "toilet_use", "scat", "defecation", "urine", "peeing",
        "golden_shower", "guro", "gore", "torture", "bondage", "bdsm", "shibari", "hogtie",
        "ball_gag", "ring_gag", "blindfold", "collar", "leash", "whip_marks", "spanking",
        "nipple_piercing", "clitoris_piercing", "labia_piercing", "bestiality", "incest", "loli",
        "shota", "pregnant", "cum_inflation", "stomach_bulge", "womb_tattoo", "heart-shaped_pupils",
        "rolling_eyes", "tongue_out", "saliva", "drooling", "sweat", "wet_pussy", "pussy_juice",
        "pussy_juice_drip_through_clothes", "erection", "veiny_penis", "penis_awe", "large_penis",
        "huge_penis", "horse_penis", "dog_penis", "tentacle_penis", "testicle_sucking",
        "testicle_grab", "ass_to_ass", "ass_grab", "underboob", "sideboob", "cameltoe",
        "cleft_of_venus", "spread_pussy", "spread_anus", "anus", "testicles", "covered_testicles",
        "clothed_sex", "clothed_female_nude_male", "naked_apron", "naked_scarf", "naked_tabard",
        "bottomless", "topless", "no_bra", "no_panties", "panties_aside", "panty_pull", "panty_lift",
        "skirt_lift", "dress_lift", "clothes_lift", "shirt_lift", "bra_lift", "sports_bra_lift",
        "bikini_lift", "thong_aside", "fishnet_bodysuit", "crotchless_pantyhose", "crotchless_panties",
        "nipple_slip", "wardrobe_malfunction"
    )
}
