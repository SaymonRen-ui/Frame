package com.saymon.frame.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

private const val EXTERNAL_DOCS = "com.android.externalstorage.documents"

/**
 * Открывает папку в системном проводнике (DocumentsUI).
 * Возвращает false, если открыть не удалось.
 */
fun openFolder(context: Context, folder: File): Boolean {
    val rel = try {
        folder.relativeTo(Environment.getExternalStorageDirectory())
            .invariantSeparatorsPath
    } catch (_: Exception) {
        folder.name
    }
    val docId = "primary:" + rel.trim('/')
    val uri: Uri = DocumentsContract.buildDocumentUri(EXTERNAL_DOCS, docId)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun openFolder(context: Context, folderUri: Uri): Boolean {
    // Для tree-URI проводник хочет document-URI, иначе ACTION_VIEW не находит папку.
    val target = try {
        val docId = DocumentsContract.getTreeDocumentId(folderUri)
        DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
    } catch (_: Exception) {
        folderUri
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = target
        type = DocumentsContract.Document.MIME_TYPE_DIR
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
