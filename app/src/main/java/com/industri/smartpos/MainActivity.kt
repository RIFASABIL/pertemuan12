package com.industri.smartpos

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var etNominal: EditText
    private lateinit var etQty: EditText
    private lateinit var btnHitung: Button
    private lateinit var tvHasil: TextView
    private lateinit var btnGoToPos: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNominal = findViewById(R.id.etNominal)
        etQty = findViewById(R.id.etQty)
        btnHitung = findViewById(R.id.btnHitung)
        tvHasil = findViewById(R.id.tvHasil)
        btnGoToPos = findViewById(R.id.btnGoToPos)

        btnHitung.setOnClickListener {
            hitungTransaksi()
        }

        btnGoToPos.setOnClickListener {
            val intent = Intent(this, PosActivity::class.java)
            startActivity(intent)
        }
    }

    private fun hitungTransaksi() {
        val nominalStr = etNominal.text.toString()
        val qtyStr = etQty.text.toString()

        // BREAKPOINT 1: Pasang breakpoint pada baris Timber.d untuk inspect nilai nominalStr & qtyStr
        Timber.d(
            "Menghitung transaksi kasir: Nominal=%s, Qty=%s",
            nominalStr,
            qtyStr
        )

        // BREAKPOINT 2: Step Over / Step Into pada blok runCatching untuk evaluasi variabel
        val kalkulasiResult = runCatching {
            val nominal = nominalStr.toDouble()
            val qty = qtyStr.toInt()

            if (qty <= 0) {
                throw IllegalArgumentException(
                    "Kuantitas barang minimal 1"
                )
            }

            nominal * qty
        }

        kalkulasiResult.onSuccess { total ->
            Timber.i(
                "Kalkulasi sukses: Total bayar Rp %,.2f",
                total
            )

            tvHasil.text =
                "Total Transaksi: Rp %,.2f".format(total)
        }.onFailure { error ->

            Timber.e(
                error,
                "Terjadi kesalahan kalkulasi input kasir"
            )

            tvHasil.text =
                "Error Input: ${error.message ?: "Input tidak valid"}"
        }
    }
}
