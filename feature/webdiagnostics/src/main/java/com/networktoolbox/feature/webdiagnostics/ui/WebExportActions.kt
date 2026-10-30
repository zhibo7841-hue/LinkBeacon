package com.networktoolbox.feature.webdiagnostics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.export.*

enum class WebExportAction { COPY, SAVE_TXT, SAVE_MARKDOWN, SAVE_PDF, SHARE_PDF }

@Composable
fun WebExportActions(source:WebExportSource?,busy:Boolean,onAction:(WebExportDocument,WebExportAction)->Unit) {
    if(source?.available!=true) return
    var frozen by remember(source.key) {mutableStateOf<WebExportDocument?>(null)}
    val chinese=LocalConfiguration.current.locales[0].language=="zh"
    TextButton(enabled=!busy,onClick={frozen=WebExportMapper.freeze(source,chinese,System.currentTimeMillis())},
        modifier=Modifier.testTag("web_export_open")) {Text(stringResource(R.string.web_export_open))}
    frozen?.let {doc->
        AlertDialog(onDismissRequest={frozen=null},title={Text(stringResource(R.string.web_export_open))},
            text={Column {
                Text(stringResource(R.string.web_export_notice))
                listOf(WebExportAction.COPY to R.string.web_export_copy,WebExportAction.SAVE_TXT to R.string.web_export_txt,
                    WebExportAction.SAVE_MARKDOWN to R.string.web_export_markdown,WebExportAction.SAVE_PDF to R.string.web_export_pdf,
                    WebExportAction.SHARE_PDF to R.string.web_export_share).forEach {(action,label)->
                    TextButton(enabled=!busy,onClick={frozen=null;onAction(doc,action)},
                        modifier=Modifier.testTag("web_export_${action.name.lowercase()}")) {Text(stringResource(label))}
                }
            }},confirmButton={TextButton(onClick={frozen=null}) {Text(stringResource(R.string.web_export_close))}})
    }
}
