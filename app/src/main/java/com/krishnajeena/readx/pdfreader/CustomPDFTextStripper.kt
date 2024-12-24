package com.krishnajeena.readx.pdfreader

import android.util.Log
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

class CustomPDFTextStripper : PDFTextStripper() {
    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        super.writeString(text, textPositions)

        for (textPosition in textPositions) {
            Log.d(
                "PDFText",
                "Text: ${textPosition.unicode}, X: ${textPosition.xDirAdj}, Y: ${textPosition.yDirAdj}, Width: ${textPosition.widthDirAdj}, Height: ${textPosition.heightDir}"
            )
        }
    }
}