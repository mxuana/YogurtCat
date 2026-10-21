package com.example.change2proimage

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OcListActivity : BaseActivity() {
    private lateinit var store: WorkspaceStore
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oc_list)
        store = WorkspaceStore(this)
        container = findViewById(R.id.ocContainer)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.addOc).setOnClickListener {
            startActivity(Intent(this, OcEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    private fun render() {
        container.removeAllViews()
        val ocs = store.getOcs()
        if (ocs.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "还没有 OC\n点击上方“新建 OC”，保存常用角色的参考图与人物提示词。"
                textSize = 14f
                setTextColor(Color.parseColor("#777187"))
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(48), dp(20), dp(48))
            })
            return
        }

        ocs.forEach { oc -> container.addView(createOcCard(oc)) }
    }

    private fun createOcCard(oc: OcProfile): View {
        val card = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#E4DFF1")
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            isClickable = true
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val coverCard = MaterialCardView(this).apply {
            radius = dp(17).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#F1EEFA"))
            layoutParams = LinearLayout.LayoutParams(dp(106), dp(128))
        }
        val cover = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "${oc.name} 封面"
        }
        val firstRef = oc.referenceUris.firstOrNull()
        if (firstRef != null) {
            Glide.with(this).load(Uri.parse(firstRef)).centerCrop().into(cover)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
            cover.setPadding(dp(30), dp(40), dp(30), dp(40))
            cover.imageTintList = ColorStateList.valueOf(Color.parseColor("#7868C8"))
        }
        coverCard.addView(cover)
        row.addView(coverCard)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = oc.name
            textSize = 19f
            setTextColor(Color.parseColor("#171525"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        titleRow.addView(MaterialButton(this).apply {
            text = "⋮"
            textSize = 20f
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#6F687D"))
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { showOcMenu(oc) }
        })
        content.addView(titleRow)

        content.addView(TextView(this).apply {
            text = "${oc.referenceUris.size} 张参考图"
            textSize = 12f
            setTextColor(Color.parseColor("#6654BC"))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#F0ECFF"))
            }
            setPadding(dp(9), dp(4), dp(9), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })

        content.addView(TextView(this).apply {
            text = oc.prompt.ifBlank { "未填写人物提示词" }
            textSize = 13f
            setTextColor(Color.parseColor("#756F82"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        buttons.addView(MaterialButton(this).apply {
            text = "编辑"
            setAllCaps(false)
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F2F0F8"))
            setTextColor(Color.parseColor("#514A61"))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@OcListActivity, OcEditActivity::class.java).putExtra("oc_id", oc.id))
            }
        })
        buttons.addView(MaterialButton(this).apply {
            text = "使用"
            setAllCaps(false)
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6150B9"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener { useOc(oc) }
        })
        content.addView(buttons)
        row.addView(content)
        card.addView(row)
        card.setOnClickListener { useOc(oc) }
        return card
    }

    private fun useOc(oc: OcProfile) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("apply_oc_id", oc.id)
        startActivity(intent)
        finish()
    }

    private fun showOcMenu(oc: OcProfile) {
        AlertDialog.Builder(this)
            .setTitle(oc.name)
            .setItems(arrayOf("编辑", "删除")) { _, which ->
                if (which == 0) {
                    startActivity(Intent(this, OcEditActivity::class.java).putExtra("oc_id", oc.id))
                } else {
                    confirmDelete(oc)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(oc: OcProfile) {
        AlertDialog.Builder(this)
            .setTitle("删除 ${oc.name}？")
            .setMessage("只删除本机保存的 OC 配置，不会删除手机相册里的原图。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> store.deleteOc(oc.id); render() }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
