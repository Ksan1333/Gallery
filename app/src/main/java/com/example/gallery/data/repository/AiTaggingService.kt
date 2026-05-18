package com.example.gallery.data.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.withContext

class AiTaggingService(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    // 英語ラベルから日本語および特定分野タグへのマッピング (Pixiv風を含む)
    private val labelMap = mapOf(
        "Cat" to listOf("猫", "ケモ耳"),
        "Dog" to listOf("犬", "動物"),
        "Food" to listOf("食べ物", "料理"),
        "Flower" to listOf("花", "植物"),
        "Mountain" to listOf("山", "風景"),
        "Sea" to listOf("海", "水辺"),
        "Sky" to listOf("空", "青空"),
        "Tree" to listOf("木", "自然"),
        "Human" to listOf("人物"),
        "Person" to listOf("人物"),
        "Smile" to listOf("笑顔"),
        "Girl" to listOf("女の子", "美少女"),
        "Boy" to listOf("男の子", "ショタ"),
        "Animation" to listOf("アニメ", "イラスト"),
        "Cartoon" to listOf("マンガ", "2D"),
        "Art" to listOf("アート", "創作"),
        "Lingerie" to listOf("下着", "R-18"),
        "Swimwear" to listOf("水着", "スク水"),
        "Undergarment" to listOf("下着", "ぱんつ"),
        "Erotica" to listOf("R-18", "エロ"),
        "Abdomen" to listOf("お腹", "へそ"),
        "Thigh" to listOf("太もも", "絶対領域"),
        "Long hair" to listOf("ロングヘア"),
        "Short hair" to listOf("ショートヘア"),
        "Black hair" to listOf("黒髪"),
        "Brown hair" to listOf("茶髪"),
        "Blond hair" to listOf("金髪"),
        "Pink hair" to listOf("桃髪", "ピンク髪"),
        "Blue hair" to listOf("青髪"),
        "Purple hair" to listOf("紫髪"),
        "Red hair" to listOf("赤髪"),
        "Green hair" to listOf("緑髪"),
        "White hair" to listOf("白髪"),
        "Silver hair" to listOf("銀髪"),
        "Eyewear" to listOf("眼鏡", "めがねっこ"),
        "Uniform" to listOf("制服"),
        "School uniform" to listOf("学生服", "セーラー服"),
        "Sailor suit" to listOf("セーラー服"),
        "Dress" to listOf("ドレス", "お嬢様"),
        "Skirt" to listOf("スカート", "ミニスカ"),
        "Leggings" to listOf("タイツ", "ストッキング"),
        "Sock" to listOf("靴下", "ニーソ"),
        "Garter belt" to listOf("ガーターベルト"),
        "Stockings" to listOf("ストッキング", "黒タイツ"),
        "Pantyhose" to listOf("パンスト", "タイツ"),
        "High heels" to listOf("ハイヒール"),
        "Hat" to listOf("帽子", "ベレー帽"),
        "Maid" to listOf("メイド", "メイド服"),
        "Nurse" to listOf("ナース", "看護師"),
        "Police" to listOf("ポリス", "女警"),
        "Kimono" to listOf("着物", "和服"),
        "Yukata" to listOf("浴衣", "祭り"),
        "Bunny girl" to listOf("バニーガール"),
        "Neko" to listOf("猫耳", "ネコミミ"),
        "Ear" to listOf("ケモ耳"),
        "Tail" to listOf("しっぽ"),
        "Wing" to listOf("翼", "天使"),
        "Demon" to listOf("悪魔", "ツノ"),
        "Elf" to listOf("エルフ", "耳長"),
        "Vampire" to listOf("吸血鬼"),
        "Lingerie" to listOf("下着", "ランジェリー", "R-18"),
        "Undergarment" to listOf("下着", "ぱんつ", "ブラジャー"),
        "Swimwear" to listOf("水着", "競泳水着", "ビキニ"),
        "Bikini" to listOf("ビキニ", "極小ビキニ"),
        "Erotica" to listOf("R-18", "エロ", "アダルト"),
        "Nudity" to listOf("ヌード", "全裸", "R-18"),
        "Breast" to listOf("おっぱい", "巨乳", "美乳"),
        "Chest" to listOf("おっぱい", "乳首"),
        "Buttock" to listOf("お尻", "尻神様", "美尻"),
        "Leg" to listOf("脚", "足フェチ", "生足"),
        "Foot" to listOf("足", "素足", "足裏"),
        "Arm" to listOf("腕", "脇"),
        "Abdomen" to listOf("お腹", "へそ", "くびれ"),
        "Thigh" to listOf("太もも", "絶対領域"),
        "Cleavage" to listOf("谷間", "胸元"),
        "Pubic hair" to listOf("アンダーヘア", "R-18"),
        "Human skin" to listOf("肌色", "露出"),
        "Expression" to listOf("表情", "赤面"),
        "Blush" to listOf("照れ", "赤面"),
        "Bed" to listOf("ベッド", "寝起き", "誘惑"),
        "Beach" to listOf("海", "夏"),
        "Sunset" to listOf("夕焼け", "黄昏"),
        "Night" to listOf("夜", "夜景"),
        "Forest" to listOf("森", "幻想的"),
        "Room" to listOf("室内", "背景"),
        "City" to listOf("都会", "ビル"),
        "Building" to listOf("建物"),
        "Car" to listOf("車"),
        "Space" to listOf("宇宙", "SF"),
        "Robot" to listOf("ロボット", "メカ"),
        "Weapon" to listOf("武器"),
        "Sword" to listOf("剣", "日本刀"),
        "Gun" to listOf("銃", "ミリタリー"),
        "Art" to listOf("アート", "イラスト"),
        "Animation" to listOf("アニメ", "2D"),
        "Cartoon" to listOf("マンガ", "モノクロ"),
        "Gothic" to listOf("ゴシック", "ゴスロリ"),
        "Lolita" to listOf("ロリ", "合法ロリ"),
        "Body" to listOf("肢体", "裸体"),
        "Bare feet" to listOf("素足", "裸足"),
        "Sitting" to listOf("座りポーズ"),
        "Lying" to listOf("寝そべり"),
        "Standing" to listOf("立ちポーズ"),
        "Back" to listOf("背中", "後ろ姿"),
        "Wet" to listOf("濡れ色", "透け"),
        "Sweat" to listOf("汗", "エロ"),
        "Blonde" to listOf("金髪"),
        "Brunette" to listOf("茶髪"),
        "Crotch" to listOf("股間", "クチュクチュ"),
        "Navel" to listOf("おへそ"),
        "Armpit" to listOf("脇", "腋"),
        "Shoulder" to listOf("肩", "オフショル"),
        "Nipple" to listOf("乳首", "B地区"),
        "Ahegao" to listOf("アヘ顔"),
        "Fingering" to listOf("指いじり"),
        "Tentacle" to listOf("触手"),
        "BDSM" to listOf("緊縛"),
        "Rope" to listOf("縄", "亀甲縛り"),
    )

    private val r18Labels = setOf(
        "Lingerie", "Erotica", "Nudity", "Pubic hair", "Crotch", "Nipple", "Ahegao", "Fingering", "Tentacle", "BDSM", "Rope"
    )
    
    private val r15Labels = setOf(
        "Swimwear", "Bikini", "Undergarment", "Breast", "Buttock", "Cleavage", "Wet", "Sweat"
    )

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f) // 複数タグ取得のため閾値を少し下げる
            .build()
    )

    suspend fun runAiTagging(
        mediaList: List<MediaData>,
        limit: Int = Int.MAX_VALUE, // デフォルトを全件に
        onProgress: (Int, Int) -> Unit
    ) = withContext<Unit>(Dispatchers.IO) {
        val allMetadata = mediaDao.getAllMetadata()
        val analyzedUris = allMetadata.filter { it.isAiAnalyzed }.map { it.uri }.toSet()
        
        val targetList = mediaList.filter { !it.isVideo && it.uri !in analyzedUris }.take(limit)
        Log.d("AiTaggingService", "Starting AI tagging for ${targetList.size} new images.")

        targetList.forEachIndexed { index, media ->
            try {
                analyzeSingle(media)
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, targetList.size)
                }
            } catch (e: Exception) {
                Log.e("AiTaggingService", "Error processing AI tagging for ${media.uri}", e)
            }
        }
    }

    suspend fun analyzeSingle(media: MediaData) = withContext(Dispatchers.IO) {
        if (media.isVideo) return@withContext
        
        kotlinx.coroutines.yield()
        val image = InputImage.fromFilePath(context, media.uri.toUri())
        val labels = labeler.process(image).asDeferred().await()
        
        var ageRating = "SFW"
        val detectedTags = mutableSetOf<String>()
        labels.forEach { label ->
            val englishLabel = label.text
            
            if (r18Labels.contains(englishLabel)) ageRating = "R18"
            else if (r15Labels.contains(englishLabel) && ageRating != "R18") ageRating = "R15"

            val translated = labelMap[englishLabel]
            if (translated != null) {
                detectedTags.addAll(translated)
            }
        }
        
        detectedTags.forEach { tagName ->
            mediaDao.insertTag(TagEntity(media.uri, tagName))
        }

        val current = mediaDao.getMetadata(media.uri)
        mediaDao.insertMetadata(
            MediaMetadataEntity(
                uri = media.uri,
                isFavorite = current?.isFavorite ?: false,
                colorComposition = current?.colorComposition,
                ageRating = ageRating,
                isAiAnalyzed = true
            )
        )
    }
}
