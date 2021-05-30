package com.example.rumb_test

import android.content.ContentValues
import android.database.sqlite.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.*


class MainActivity : AppCompatActivity() {

    // Работа с базой данных
    lateinit var db: DBHandler

    // Регулярные выражения для парсинга ввода пользователя
    private val regexBL = "^(\\d+) (\\d+) (\\d{1,2}|\\d{1,2}\\.\\d+)\$".toRegex()
    private val regexXYZ = "^(-?\\d+|-?\\d+\\.\\d+)\$".toRegex()

    // Конвертация из Wgs 84 в Пулково 42 и обратно
    private fun wgs84Pulk42(x1: Double, y1: Double, z1: Double, isToPulk: Boolean): ArrayList<Double> {
        val dx = 23.57
        val dy = -140.95
        val dz = -79.8
        val rx = 0.0
        val ry: Double = (-0.35 / 3600) * PI / 180
        val rz: Double = (-0.79 / 3600) * PI / 180
        val m = 1 - (-0.22 / 1000000)
        val x2: Double
        val y2: Double
        val z2: Double

        if (isToPulk) {
            x2 = round((x1 * m + y1 * m * (-1) * rz + m * ry * z1 - dx) * 1000.0) / 1000.0
            y2 = round((m * rz * x1 + y1 * m + m * (-1) * rx * z1 - dy) * 1000.0) / 1000.0
            z2 = round((m * (-ry) * x1 + rx * m * ry + m * z1 - dz) * 1000.0) / 1000.0
        } else {
            x2 =
                x1 - (round((x1 * m + y1 * m * (-1) * rz + m * ry * z1 - dx) * 1000.0) / 1000.0) + x1
            y2 =
                y1 - (round((m * rz * x1 + y1 * m + m * (-1) * rx * z1 - dy) * 1000.0) / 1000.0) + y1
            z2 = z1 - (round((m * (-ry) * x1 + rx * m * ry + m * z1 - dz) * 1000.0) / 1000.0) + z1
        }
        return arrayListOf(x2, y2, z2)
    }

    // Конвертация из Xyz в Blh
    private fun fromXyzToBlh(x: Double, y: Double, z: Double, isFromWgs84: Boolean, isToWgs84: Boolean): ArrayList<String> {
        var x1 = x
        var y1 = y
        var z1 = z

        var bp = 0
        var os = .0

        if (isFromWgs84 && isToWgs84) {
            bp = 6378137
            os = 298.257223563
        } else if (isFromWgs84 && !isToWgs84) {
            val list = wgs84Pulk42(x, y, z, true)
            x1 = list[0]; y1 = list[1]; z1 = list[2]
            bp = 6378245
            os = 298.3
        } else if (!isFromWgs84 && isToWgs84) {
            val list = wgs84Pulk42(x, y, z, false)
            x1 = list[0]; y1 = list[1]; z1 = list[2]
            bp = 6378137
            os = 298.257223563
        } else {
            bp = 6378245
            os = 298.3
        }

        val f = 1 / os
        val e = 2 * f - f * f

        val p = sqrt(x1 * x1 + y1 * y1)
        val r = sqrt(p * p + z1 * z1)
        val m = atan((z1 / p) * ((1 - f) + (e * bp) / r))
        val ltl = z1 * (1 - f) + e * bp * sin(m) * sin(m) * sin(m)
        val lbl = (1 - f) * (p - e * bp * cos(m) * cos(m) * cos(m))
        val long = atan(y1 / x1).let {
            (if (it < 0) it + PI else it) / PI * 180
        }
        val lat = atan(ltl / lbl) / PI * 180

        val bDeg = floor(lat)
        val bMin = floor((lat - bDeg) * 60.0)
        val bSec = (lat - bDeg - (bMin / 60.0)) * 3600.0
        val b = "${bDeg.toInt()} ${bMin.toInt()} $bSec"
        val lDeg = floor(long)
        val lMin = floor((long - lDeg) * 60.0)
        val lSec = (long - lDeg - (lMin / 60.0)) * 3600.0
        val l = "${lDeg.toInt()} ${lMin.toInt()} $lSec"
        val h = (round((p * cos(atan(ltl/lbl)) + z1 * sin(atan(ltl/lbl)) - bp * sqrt(1 - e * sin(atan(ltl/lbl)) * sin(atan(ltl/lbl)))) * 1000) / 1000).toString()

        return arrayListOf(b, l, h)
    }

    // Конвертация из Blh в Xyz
    private fun fromBlhToXyz(b: ArrayList<Double>, l: ArrayList<Double>, h: Double, isFromWgs84: Boolean, isToWgs84: Boolean): ArrayList<String> {
        var bp = 0
        var os = .0

        val (bDeg, bMin, bSec) = b
        val (lDeg, lMin, lSec) = l

        if (isFromWgs84) {
            bp = 6378137
            os = 298.257223563
        } else {
            bp = 6378245
            os = 298.3
        }

        val f = 1 / os
        val e = 2 * f - f * f

        val bDec = (bDeg.absoluteValue + bMin.absoluteValue / 60 + bSec.absoluteValue / 3600).let {
            (if (bDeg < 0) -it else (if (bMin < 0) -it else (if (bSec < 0) -it else it)))
        }
        val bRad = bDec / 180 * PI

        val lDec = lDeg.absoluteValue + lMin.absoluteValue / 60 + lSec.absoluteValue / 3600.let {
            (if (lDeg < 0) -it else (if (lMin < 0) -it else (if (lSec < 0) -it else it)))
        }
        val lRad = lDec / 180 * PI

        val n = bp / sqrt(1 - e * sin(bRad) * sin(bRad))

        val x = (n + h) * cos(bRad) * cos(lRad)
        val y = (n + h) * cos(bRad) * sin(lRad)
        val z = ((1 - e) * n + h) * sin(bRad)

        if (isFromWgs84 && !isToWgs84) {
            val list = wgs84Pulk42(x, y, z, true)
            return arrayListOf(list[0].toString(), list[1].toString(), list[2].toString())
        } else if (!isFromWgs84 && isToWgs84) {
            val list = wgs84Pulk42(x, y, z, false)
            return arrayListOf(list[0].toString(), list[1].toString(), list[2].toString())
        }

        return arrayListOf(x.toString(), y.toString(), z.toString())
    }

    val itemList = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DBHandler(this)

        // Обновление списка сохранений из БД
        db.readableDatabase.query(
            "configs",
            arrayOf("saveName"),
            null,
            arrayOf(),
            null, null, null
        ).use {
            while (it.moveToNext()) {
                itemList.add(it.getString(it.getColumnIndex("saveName")))
            }
            val adapterList = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_list_item_1,
                itemList
            )
            configList.adapter = adapterList
        }

        fromGeocentrRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                fromTextInputLayout1.hint = "Широта"
                fromTextInputLayout2.hint = "Долгота"
                fromTextInputLayout3.hint = "Высота"
            } else {
                fromTextInputLayout1.hint = "X"
                fromTextInputLayout2.hint = "Y"
                fromTextInputLayout3.hint = "Z"
            }
        }

        toGeocentrRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                toTextInputLayout1.hint = "X"
                toTextInputLayout2.hint = "Y"
                toTextInputLayout3.hint = "Z"
            } else {
                toTextInputLayout1.hint = "Широта"
                toTextInputLayout2.hint = "Долгота"
                toTextInputLayout3.hint = "Высота"
            }
        }

        resultButton.setOnClickListener {
            if (fromGeocentrRadioButton.isChecked) {
                val regX = regexXYZ.find(fromTextInput1.text.toString())
                val regY = regexXYZ.find(fromTextInput2.text.toString())
                val regZ = regexXYZ.find(fromTextInput3.text.toString())
                if (regX != null && regY != null && regZ != null) {
                    val x = regX.destructured.component1().toDouble()
                    val y = regY.destructured.component1().toDouble()
                    val z = regZ.destructured.component1().toDouble()
                    if (!toGeocentrRadioButton.isChecked) {
                        val result = fromXyzToBlh(
                            x,
                            y,
                            z,
                            fromWgs84RadioButton.isChecked,
                            toWgs84RadioButton.isChecked
                        )
                        toTextInput1.setText(result[0])
                        toTextInput2.setText(result[1])
                        toTextInput3.setText(result[2])
                    } else {
                        if (fromPulk42RadioButton.isChecked == toPulk42RadioButton.isChecked) {
                            toTextInput1.setText(fromTextInput1.text.toString())
                            toTextInput2.setText(fromTextInput2.text.toString())
                            toTextInput3.setText(fromTextInput3.text.toString())
                        } else {
                            val result = wgs84Pulk42(x, y, z, !toWgs84RadioButton.isChecked)
                            toTextInput1.setText(result[0].toString())
                            toTextInput2.setText(result[1].toString())
                            toTextInput3.setText(result[2].toString())
                        }
                    }
                }
                else if (regX == null)
                    fromTextInput1.error = "Ошибка ввода"
                else if (regY == null)
                    fromTextInput2.error = "Ошибка ввода"
                else
                    fromTextInput3.error = "Ошибка ввода"

            } else if (fromGeodezRadioButton.isChecked) {
                val regB = regexBL.find(fromTextInput1.text.toString())
                val regL = regexBL.find(fromTextInput2.text.toString())
                val regH = regexXYZ.find(fromTextInput3.text.toString())
                if (regB != null && regL != null && regH != null) {
                    val (bDeg, bMin, bSec) = regB.destructured.toList().map { it.toDouble() }
                    val (lDeg, lMin, lSec) = regL.destructured.toList().map { it.toDouble() }
                    val listB = arrayListOf(bDeg, bMin, bSec)
                    val listL = arrayListOf(lDeg, lMin, lSec)
                    val h = regH.destructured.component1().toDouble()
                    if (toGeocentrRadioButton.isChecked) {
                        val result = fromBlhToXyz(
                            listB,
                            listL,
                            h,
                            fromWgs84RadioButton.isChecked,
                            toWgs84RadioButton.isChecked
                        )
                        toTextInput1.setText(result[0])
                        toTextInput2.setText(result[1])
                        toTextInput3.setText(result[2])
                    } else {
                        if (fromWgs84RadioButton.isChecked == toWgs84RadioButton.isChecked) {
                            toTextInput1.setText(fromTextInput1.text.toString())
                            toTextInput2.setText(fromTextInput2.text.toString())
                            toTextInput3.setText(fromTextInput3.text.toString())
                        } else {
                            val listBlhToXyz = fromBlhToXyz(
                                listB,
                                listL,
                                h,
                                fromWgs84RadioButton.isChecked,
                                toWgs84RadioButton.isChecked
                            )
                            val listWgsPulk = wgs84Pulk42(
                                listBlhToXyz[0].toDouble(), listBlhToXyz[1].toDouble(),
                                listBlhToXyz[2].toDouble(), !toWgs84RadioButton.isChecked
                            )
                            val result = fromXyzToBlh(
                                listWgsPulk[0],
                                listWgsPulk[1],
                                listWgsPulk[2],
                                fromWgs84RadioButton.isChecked,
                                toWgs84RadioButton.isChecked
                            )
                            toTextInput1.setText(result[0])
                            toTextInput2.setText(result[1])
                            toTextInput3.setText(result[2])
                        }

                    }

                } else if (regB == null) {
                    fromTextInput1.error = "Ошибка ввода"
                } else if (regL == null) {
                    fromTextInput2.error = "Ошибка ввода"
                } else {
                    fromTextInput3.error = "Ошибка ввода"
                }
                

            }

        }

        saveConfigButton.setOnClickListener {
            val saveName = configNameTextInput.text.toString()
            val isFromGeodez = fromGeodezRadioButton.isChecked
            val isToGeodez = toGeodezRadioButton.isChecked
            val isFromWgs = fromWgs84RadioButton.isChecked
            val isToWgs = toWgs84RadioButton.isChecked
            val inputFrom1: String
            val inputFrom2: String
            val inputFrom3: String

            if (db.readableDatabase.query(
                    "configs",
                    arrayOf(),
                    "saveName = ?",
                    arrayOf(saveName),
                    null, null, null
                ).use { it.count > 0 }) {
                inputFrom1 = fromTextInput1.text.toString()
                inputFrom2 = fromTextInput2.text.toString()
                inputFrom3 = fromTextInput3.text.toString()
                db.writableDatabase.update(
                    "configs",
                    ContentValues().apply {
                        put("isFromGeodez", if (isFromGeodez) 1 else 0)
                        put("isToGeodez", if (isToGeodez) 1 else 0)
                        put("fromWgs", if (isFromWgs) 1 else 0)
                        put("toWgs", if (isToWgs) 1 else 0)
                        put("inputFrom1", inputFrom1)
                        put("inputFrom2", inputFrom2)
                        put("inputFrom3", inputFrom3)
                    },
                    "saveName = ?",
                    arrayOf(saveName)
                )
            }
            else if (fromGeocentrRadioButton.isChecked) {
                val regX = regexXYZ.find(fromTextInput1.text.toString())
                val regY = regexXYZ.find(fromTextInput2.text.toString())
                val regZ = regexXYZ.find(fromTextInput3.text.toString())

                if (regX == null) {
                    fromTextInput1.error = "Ошибка ввода"
                    return@setOnClickListener
                }
                if (regY == null) {
                    fromTextInput2.error = "Ошибка ввода"
                    return@setOnClickListener
                }
                if (regZ == null) {
                    fromTextInput3.error = "Ошибка ввода"
                    return@setOnClickListener
                }
                inputFrom1 = fromTextInput1.text.toString()
                inputFrom2 = fromTextInput2.text.toString()
                inputFrom3 = fromTextInput3.text.toString()

                db.writableDatabase.insert(
                    "configs",
                    null,
                    ContentValues().apply {
                        put("saveName", saveName)
                        put("isFromGeodez", if (isFromGeodez) 1 else 0)
                        put("isToGeodez", if (isToGeodez) 1 else 0)
                        put("fromWgs", if (isFromWgs) 1 else 0)
                        put("toWgs", if (isToWgs) 1 else 0)
                        put("inputFrom1", inputFrom1)
                        put("inputFrom2", inputFrom2)
                        put("inputFrom3", inputFrom3)
                    }
                )
                itemList.add(saveName)
            } else if (fromGeodezRadioButton.isChecked) {
                val regB = regexBL.find(fromTextInput1.text.toString())
                val regL = regexBL.find(fromTextInput2.text.toString())
                val regH = regexXYZ.find(fromTextInput3.text.toString())

                if (regB == null) {
                    fromTextInput1.error = "Ошибка ввода"
                    return@setOnClickListener
                }
                if (regL == null) {
                    fromTextInput2.error = "Ошибка ввода"
                    return@setOnClickListener
                }
                if (regH == null) {
                    fromTextInput3.error = "Ошибка ввода"
                    return@setOnClickListener
                }

                inputFrom1 = fromTextInput1.text.toString()
                inputFrom2 = fromTextInput2.text.toString()
                inputFrom3 = fromTextInput3.text.toString()
                db.writableDatabase.insert(
                    "configs",
                    null,
                    ContentValues().apply {
                        put("saveName", saveName)
                        put("isFromGeodez", if (isFromGeodez) 1 else 0)
                        put("isToGeodez", if (isToGeodez) 1 else 0)
                        put("fromWgs", if (isFromWgs) 1 else 0)
                        put("toWgs", if (isToWgs) 1 else 0)
                        put("inputFrom1", inputFrom1)
                        put("inputFrom2", inputFrom2)
                        put("inputFrom3", inputFrom3)
                    }
                )
                itemList.add(saveName)
            }

        }

        deleteSaveButton.setOnClickListener {
            val saveName = configList.selectedItem.toString()

            db.writableDatabase.delete(
                "configs",
                "saveName = ?",
                arrayOf(saveName)
            )

            itemList.remove(saveName)
            configList.adapter
        }

        aboutButton.setOnClickListener {
            val alertDialog: AlertDialog = AlertDialog.Builder(this@MainActivity).create()
            alertDialog.setTitle("О программе")
            alertDialog.setMessage("Эта программа предназначена для конвертации геоцентрических и геодезических координат.")
            alertDialog.show()
        }

        configList.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
                val saveName = itemList[i]

                db.readableDatabase.query(
                    "configs",
                    null,
                    "saveName = ?",
                    arrayOf(saveName),
                    null, null, null
                ).use {
                    it.moveToNext()
                    arrayListOf(fromGeocentrRadioButton, fromGeodezRadioButton)[it.getInt(it.getColumnIndex("isFromGeodez"))].isChecked = true
                    arrayListOf(toGeocentrRadioButton, toGeodezRadioButton)[it.getInt(it.getColumnIndex("isToGeodez"))].isChecked = true
                    arrayListOf(fromPulk42RadioButton, fromWgs84RadioButton)[it.getInt(it.getColumnIndex("fromWgs"))].isChecked = true
                    arrayListOf(toPulk42RadioButton, toWgs84RadioButton)[it.getInt(it.getColumnIndex("toWgs"))].isChecked = true
                    fromTextInput1.setText(it.getString(it.getColumnIndex("inputFrom1")))
                    fromTextInput2.setText(it.getString(it.getColumnIndex("inputFrom2")))
                    fromTextInput3.setText(it.getString(it.getColumnIndex("inputFrom3")))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }
    }


}
