package com.example.change2proimage

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

class StickerItemEditActivity: BaseActivity(){
    private lateinit var store:StickerStore; private lateinit var name:EditText; private lateinit var prompt:EditText; private lateinit var imageInfo:TextView
    private var imageUri=""; private var id:String?=null
    private val picker=registerForActivityResult(ActivityResultContracts.PickVisualMedia()){u: Uri?-> if(u!=null){ runCatching{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}; imageUri=u.toString(); imageInfo.text="已选择模板图" } }
    override fun onCreate(b:Bundle?){super.onCreate(b);store=StickerStore(this);id=intent.getStringExtra("item_id")
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(28));setBackgroundColor(Color.parseColor("#FFF8FC"))}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}; top.addView(Button(this).apply{text="‹";textSize=25f;setOnClickListener{finish()}},LinearLayout.LayoutParams(dp(58),dp(50))); top.addView(TextView(this).apply{text=if(id==null)"新建表情模板" else "编辑表情模板";textSize=23f;setPadding(dp(12),0,0,0)},LinearLayout.LayoutParams(0,dp(50),1f));c.addView(top)
        c.addView(label("名称"));name=EditText(this).apply{hint="例如：抱酸奶 / 开心 / 哭哭"};c.addView(name)
        c.addView(label("模板参考图（图一）")); imageInfo=TextView(this).apply{text="还没有模板图";setPadding(0,dp(8),0,dp(8))};c.addView(imageInfo);c.addView(Button(this).apply{text="选择模板图片";setOnClickListener{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}})
        c.addView(label("模板提示词"));prompt=EditText(this).apply{minLines=5;gravity=Gravity.TOP;setText("将图一表情包人物换成图二中的人物，画风、表情、动作、构图与图一保持一致；保留图二人物的发型、发色、眼睛、服装和标志性特征。")};c.addView(prompt,LinearLayout.LayoutParams(-1,dp(170)))
        c.addView(TextView(this).apply{text="批量生成时：模板图会作为图一，OC 的人物参考图作为图二及后续参考图。";setTextColor(Color.parseColor("#756D7C"));setPadding(0,dp(8),0,dp(16))})
        c.addView(Button(this).apply{text="保存表情模板";setOnClickListener{save()}},LinearLayout.LayoutParams(-1,dp(56)))
        setContentView(ScrollView(this).apply{addView(c)}); id?.let{store.getItem(it)}?.let{ name.setText(it.name);prompt.setText(it.prompt);imageUri=it.imageUri;imageInfo.text=if(imageUri.isBlank())"还没有模板图" else "已保存模板图" }
    }
    private fun save(){val n=name.text.toString().trim();if(n.isBlank()){name.error="请填写名称";return};if(imageUri.isBlank()){Toast.makeText(this,"请先选择模板图片",Toast.LENGTH_SHORT).show();return};store.saveItem(StickerTemplateItem(id=id?:java.util.UUID.randomUUID().toString(),name=n,imageUri=imageUri,prompt=prompt.text.toString().trim()));finish()}
    private fun label(s:String)=TextView(this).apply{text=s;textSize=16f;setPadding(0,dp(18),0,dp(6))};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
