package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogHttpReplaceBinding
import io.legado.app.databinding.ItemHeaderPairBinding
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.get
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postForm
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jayway.jsonpath.JsonPath

class HttpReplaceDialog : BaseDialogFragment(R.layout.dialog_http_replace, true) {

    private val binding by viewBinding(DialogHttpReplaceBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.spMethod.setAdapter(android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            arrayOf("GET", "POST")
        ))
        binding.tvAddHeader.setOnClickListener { addHeaderView() }
        addHeaderView()
        binding.tvSend.setOnClickListener { sendRequest() }
    }

    private fun addHeaderView(key: String? = null, value: String? = null) {
        val item = ItemHeaderPairBinding.inflate(layoutInflater, binding.llHeaders, false)
        item.etKey.setText(key)
        item.etValue.setText(value)
        item.ivDelete.setOnClickListener { binding.llHeaders.removeView(item.root) }
        binding.llHeaders.addView(item.root)
    }

    private fun collectHeaders(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (i in 0 until binding.llHeaders.childCount) {
            val child = binding.llHeaders.getChildAt(i)
            val item = ItemHeaderPairBinding.bind(child)
            val k = item.etKey.text.toString()
            if (k.isNotBlank()) {
                map[k] = item.etValue.text.toString()
            }
        }
        return map
    }

    private fun sendRequest() {
        val url = binding.etUrl.text.toString()
        if (url.isBlank()) return
        val paramKey = binding.etParamKey.text.toString().ifBlank { "content" }
        val headers = collectHeaders()
        val method = if (binding.spMethod.text.toString() == "POST") "POST" else "GET"
        val jsonPath = binding.etJsonPath.text.toString()
        val content = ReadBook.curTextChapter?.getContent() ?: return
        binding.tvResult.setText("")
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if (method == "GET") {
                    okHttpClient.newCallStrResponse {
                        get(url, mapOf(paramKey to content))
                        addHeaders(headers)
                    }
                } else {
                    okHttpClient.newCallStrResponse {
                        url(url)
                        addHeaders(headers)
                        postForm(mapOf(paramKey to content))
                    }
                }
            }.onSuccess { res ->
                val body = res.body ?: ""
                val sb = StringBuilder()
                sb.appendLine("url: $url")
                sb.appendLine("method: $method")
                sb.appendLine("headers: $headers")
                sb.appendLine("code: ${res.raw.code}")
                sb.appendLine("data: $body")
                if (jsonPath.isNotBlank()) {
                    kotlin.runCatching {
                        val value = JsonPath.parse(body).read<Any?>(jsonPath)
                        sb.appendLine("jsonPath: $value")
                    }
                }
                launch(Dispatchers.Main) {
                    binding.tvResult.setText(sb.toString())
                }
            }.onFailure {
                launch(Dispatchers.Main) {
                    binding.tvResult.setText(it.localizedMessage)
                }
            }
        }
    }
}
