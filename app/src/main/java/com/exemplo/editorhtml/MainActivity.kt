package com.exemplo.editorhtml

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var llm: LlmInference? = null
    private lateinit var status: TextView
    private lateinit var htmlBox: EditText
    private lateinit var askBox: EditText
    private lateinit var resultBox: EditText

    private fun campo(dica: String, linhas: Int): EditText {
        val e = EditText(this)
        e.hint = dica
        e.minLines = linhas
        e.gravity = Gravity.TOP
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 64, 32, 32)

        status = TextView(this)
        status.text = "Escolha o arquivo do modelo"
        val btnModelo = Button(this)
        btnModelo.text = "Escolher modelo (.task)"
        htmlBox = campo("Cole seu HTML aqui", 6)
        askBox = campo("O que você quer mudar?", 2)
        val btnAplicar = Button(this)
        btnAplicar.text = "Aplicar"
        resultBox = campo("Resultado aparece aqui", 6)
        val btnUsar = Button(this)
        btnUsar.text = "Usar resultado como novo HTML"

        root.addView(status)
        root.addView(btnModelo)
        root.addView(htmlBox)
        root.addView(askBox)
        root.addView(btnAplicar)
        root.addView(resultBox)
        root.addView(btnUsar)
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        btnModelo.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 1)
        }
        btnAplicar.setOnClickListener { aplicar() }
        btnUsar.setOnClickListener { htmlBox.setText(resultBox.text) }

        if (File(filesDir, "modelo.task").exists()) carregar()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req == 1 && res == RESULT_OK && uri != null) {
            status.text = "Copiando modelo... aguarde"
            thread {
                try {
                    val out = File(filesDir, "modelo.task")
                    contentResolver.openInputStream(uri)!!.use { ent ->
                        out.outputStream().use { sai -> ent.copyTo(sai) }
                    }
                    runOnUiThread { carregar() }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Erro: ${e.message}" }
                }
            }
        }
    }

    private fun carregar() {
        status.text = "Carregando IA... pode levar 1 minuto"
        thread {
            try {
                val f = File(filesDir, "modelo.task")
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(f.absolutePath)
                    .setMaxTokens(1280)
                    .build()
                llm = LlmInference.createFromOptions(this, opts)
                runOnUiThread { status.text = "IA pronta (offline)" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Erro: ${e.message}" }
            }
        }
    }

    private fun aplicar() {
        val ia = llm
        if (ia == null) {
            status.text = "A IA ainda não está carregada"
            return
        }
        status.text = "Pensando..."
        val prompt = "<|im_start|>system\nVocê edita HTML. Devolva somente o HTML completo modificado, sem explicações.<|im_end|>\n" +
            "<|im_start|>user\nHTML:\n${htmlBox.text}\n\nPedido: ${askBox.text}<|im_end|>\n" +
            "<|im_start|>assistant\n"
        thread {
            try {
                val r = ia.generateResponse(prompt)
                runOnUiThread {
                    resultBox.setText(r)
                    status.text = "IA pronta (offline)"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Erro: ${e.message}" }
            }
        }
    }
}