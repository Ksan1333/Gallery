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
        "Cat" to listOf("猫"),
        "Dog" to listOf("犬"),
        "Food" to listOf("食べ物"),
        "Flower" to listOf("花"),
        "Mountain" to listOf("山"),
        "Sea" to listOf("海"),
        "Sky" to listOf("空"),
        "Tree" to listOf("木"),
        "Human" to listOf("人物"),
        "Person" to listOf("人物"),
        "Smile" to listOf("笑顔"),
        "Girl" to listOf("女の子"),
        "Boy" to listOf("男の子"),
        "Animation" to listOf("アニメ"),
        "Cartoon" to listOf("マンガ"),
        "Art" to listOf("アート"),
        "Lingerie" to listOf("下着"),
        "Swimwear" to listOf("水着"),
        "Undergarment" to listOf("下着"),
        "Erotica" to listOf("エロ"),
        "Abdomen" to listOf("お腹"),
        "Thigh" to listOf("太もも"),
        "Long hair" to listOf("ロングヘア"),
        "Short hair" to listOf("ショートヘア"),
        "Black hair" to listOf("黒髪"),
        "Brown hair" to listOf("茶髪"),
        "Blond hair" to listOf("金髪"),
        "Pink hair" to listOf("ピンク髪"),
        "Blue hair" to listOf("青髪"),
        "Purple hair" to listOf("紫髪"),
        "Red hair" to listOf("赤髪"),
        "Green hair" to listOf("緑髪"),
        "White hair" to listOf("白髪"),
        "Silver hair" to listOf("銀髪"),
        "Eyewear" to listOf("眼鏡"),
        "Uniform" to listOf("制服"),
        "School uniform" to listOf("学生服"),
        "Sailor suit" to listOf("セーラー服"),
        "Dress" to listOf("ドレス"),
        "Skirt" to listOf("スカート"),
        "Leggings" to listOf("タイツ"),
        "Sock" to listOf("靴下"),
        "Garter belt" to listOf("ガーターベルト"),
        "Stockings" to listOf("ストッキング"),
        "Pantyhose" to listOf("パンスト"),
        "High heels" to listOf("ハイヒール"),
        "Hat" to listOf("帽子"),
        "Maid" to listOf("メイド"),
        "Nurse" to listOf("ナース"),
        "Police" to listOf("ポリス"),
        "Kimono" to listOf("着物"),
        "Yukata" to listOf("浴衣"),
        "Bunny girl" to listOf("バニーガール"),
        "Neko" to listOf("猫耳"),
        "Ear" to listOf("ケモ耳"),
        "Tail" to listOf("しっぽ"),
        "Wing" to listOf("翼"),
        "Demon" to listOf("悪魔"),
        "Elf" to listOf("エルフ"),
        "Vampire" to listOf("吸血鬼"),
        "Nudity" to listOf("ヌード"),
        "Breast" to listOf("おっぱい"),
        "Chest" to listOf("胸"),
        "Buttock" to listOf("お尻"),
        "Leg" to listOf("脚"),
        "Foot" to listOf("足"),
        "Arm" to listOf("腕"),
        "Cleavage" to listOf("谷間"),
        "Pubic hair" to listOf("アンダーヘア"),
        "Human skin" to listOf("肌"),
        "Expression" to listOf("表情"),
        "Blush" to listOf("赤面"),
        "Bed" to listOf("ベッド"),
        "Beach" to listOf("海辺"),
        "Sunset" to listOf("夕焼け"),
        "Night" to listOf("夜"),
        "Forest" to listOf("森"),
        "Room" to listOf("部屋"),
        "City" to listOf("都会"),
        "Building" to listOf("建物"),
        "Car" to listOf("車"),
        "Space" to listOf("宇宙"),
        "Robot" to listOf("ロボット"),
        "Weapon" to listOf("武器"),
        "Sword" to listOf("剣"),
        "Gun" to listOf("銃"),
        "Gothic" to listOf("ゴシック"),
        "Lolita" to listOf("ロリータ"),
        "Body" to listOf("身体"),
        "Bare feet" to listOf("裸足"),
        "Sitting" to listOf("座る"),
        "Lying" to listOf("寝そべる"),
        "Standing" to listOf("立つ"),
        "Back" to listOf("背中"),
        "Wet" to listOf("濡れ"),
        "Sweat" to listOf("汗"),
        "Blonde" to listOf("金髪"),
        "Brunette" to listOf("茶髪"),
        "Crotch" to listOf("股間"),
        "Navel" to listOf("へそ"),
        "Armpit" to listOf("脇"),
        "Shoulder" to listOf("肩"),
        "Nipple" to listOf("乳首"),
        "Angel" to listOf("天使"),
        "Princess" to listOf("姫"),
        "Queen" to listOf("女王"),
        "Knight" to listOf("騎士"),
        "Mage" to listOf("魔法使い"),
        "Witch" to listOf("魔女"),
        "Fantasy" to listOf("ファンタジー"),
        "Sci-fi" to listOf("SF"),
        "Cyberpunk" to listOf("サイバーパンク"),
        "Steampunk" to listOf("スチームパンク"),
        "Samurai" to listOf("侍"),
        "Shrine maiden" to listOf("巫女"),
        "Idol" to listOf("アイドル"),
        "Singer" to listOf("歌手"),
        "Dancer" to listOf("ダンサー"),
        "Chef" to listOf("料理人"),
        "Teacher" to listOf("教師"),
        "Office lady" to listOf("OL"),
        "Gloves" to listOf("手袋"),
        "Cape" to listOf("マント"),
        "Scarf" to listOf("マフラー"),
        "Ribbon" to listOf("リボン"),
        "Armor" to listOf("鎧"),
        "Tattoo" to listOf("タトゥー"),
        "Piercing" to listOf("ピアス"),
        "Muscular" to listOf("筋肉"),
        "Cute" to listOf("かわいい"),
        "Beautiful" to listOf("美人"),
        "Cool" to listOf("かっこいい"),
        "Sexy" to listOf("セクシー"),
        "Chibi" to listOf("ちびキャラ"),
        "Animal ears" to listOf("獣耳"),
        "Fox" to listOf("狐"),
        "Wolf" to listOf("狼"),
        "Dragon" to listOf("ドラゴン"),
        "Fairy" to listOf("妖精"),
        "Magic" to listOf("魔法"),
        "Fire" to listOf("炎"),
        "Water" to listOf("水"),
        "Ice" to listOf("氷"),
        "Lightning" to listOf("雷"),
        "Snow" to listOf("雪"),
        "Rain" to listOf("雨"),
        "Cherry blossoms" to listOf("桜"),
        "Moon" to listOf("月"),
        "Star" to listOf("星"),
        "Galaxy" to listOf("銀河")
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
            // 年齢制限ラベルはタグテーブルには入れない（メタデータ側の ageRating で一元管理）
            if (tagName != "R18" && tagName != "R15" && tagName != "SFW") {
                mediaDao.insertTag(TagEntity(media.uri, tagName))
            }
        }

        val current = mediaDao.getMetadata(media.uri)
        // 既に SFW 以外（手動設定など）になっている場合は上書きしない
        val finalAgeRating = if (current?.ageRating != null && current.ageRating != "SFW") {
            current.ageRating
        } else {
            ageRating
        }

        mediaDao.insertMetadata(
            MediaMetadataEntity(
                uri = media.uri,
                isFavorite = current?.isFavorite ?: false,
                colorComposition = current?.colorComposition,
                ageRating = finalAgeRating,
                isAiAnalyzed = true
            )
        )
    }
}
