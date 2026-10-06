package com.industri.smartpos

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PosActivity : AppCompatActivity() {

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var etBarcode: EditText
    private lateinit var etPosNominal: EditText
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: CardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var rgSimulation: RadioGroup

    private var retryAttempt = 0

    companion object {
        // PIN Supervisor Dummy untuk simulasi praktikum
        private const val DUMMY_SUPERVISOR_PIN = "1234"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinatorLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        etBarcode = findViewById(R.id.etBarcode)
        etPosNominal = findViewById(R.id.etPosNominal)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        rgSimulation = findViewById(R.id.rgSimulation)

        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            if (barcode.isEmpty()) {
                Toast.makeText(this, "Barcode wajib diisi atau dipindai", Toast.LENGTH_SHORT).show()
            } else {
                eksekusiTransaksi(barcode)
            }
        }
    }

    private fun eksekusiTransaksi(barcode: String, isAuthorizedBySupervisor: Boolean = false) {
        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        // COROUTINE EXCEPTION HANDLER
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
            Timber.e(
                exception,
                "FATAL COROUTINE ERROR TERTANGKAP: %s",
                exception.message
            )

            runOnUiThread {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                tampilkanSnackbarError(
                    "Kesalahan sistem tak terduga: ${exception.localizedMessage}",
                    canRetry = false
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            // Simulasi delay jaringan
            delay(1200)

            val nominal = etPosNominal.text.toString().toDoubleOrNull() ?: 21500.0

            // runCatching untuk penanganan error/exception transaksi
            val hasil = runCatching {
                // TUGAS MANDIRI 2: Batas Limit Otorisasi Kasir (Maksimal Rp 10.000.000)
                if (nominal > 10_000_000.0 && !isAuthorizedBySupervisor) {
                    throw CashierLimitExceededException(10_000_000.0)
                }

                // BREAKPOINT POS: Pasang breakpoint untuk memeriksa mode simulasi yang dipilih
                when (rgSimulation.checkedRadioButtonId) {
                    R.id.rbBarcodeError -> {
                        throw ProductBarcodeNotFoundException(barcode)
                    }
                    R.id.rbNetworkTimeout -> {
                        throw PaymentGatewayTimeoutException("QRIS Bank Settlement")
                    }
                    else -> {
                        PosTransaction(
                            transactionId = "TRX-2026-9901",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = nominal,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                hasil.onSuccess { trx ->
                    retryAttempt = 0 // Reset counter retry saat transaksi berhasil
                    Timber.i("Transaksi kasir berhasil dicatat: %s", trx.transactionId)

                    cardPosResult.visibility = View.VISIBLE
                    tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
                    tvPosDetails.text = """
                        ID Transaksi : ${trx.transactionId}
                        Barcode      : ${trx.barcode}
                        Produk       : ${trx.productName}
                        Total Bayar  : Rp %,.2f
                        Waktu        : Baru Saja
                    """.trimIndent().format(trx.totalAmount)

                }.onFailure { err ->
                    Timber.w("Transaksi kasir ditolak / gagal: %s", err.message)

                    when (err) {
                        is ProductBarcodeNotFoundException -> {
                            tampilkanSnackbarError(
                                err.message ?: "Barcode tidak ditemukan",
                                canRetry = false
                            )
                        }

                        is PaymentGatewayTimeoutException -> {
                            retryAttempt++
                            Timber.w("Retry attempt ke-$retryAttempt untuk Payment Gateway Timeout")

                            // TUGAS MANDIRI 1: Jika retry gagal 3 kali berturut-turut
                            if (retryAttempt >= 3) {
                                tampilkanDialogFallbackPembayaran()
                            } else {
                                tampilkanSnackbarError(
                                    err.message ?: "Koneksi gateway terputus",
                                    canRetry = true
                                )
                            }
                        }

                        is CashierLimitExceededException -> {
                            Timber.w("Limit kasir terlampaui: ${err.message}")
                            // TUGAS MANDIRI 2: Tampilkan dialog otorisasi supervisor
                            tampilkanDialogOtorisasiSupervisor(barcode, err.maxLimit)
                        }

                        else -> {
                            tampilkanSnackbarError(
                                "Kegagalan operasional: ${err.message}",
                                canRetry = true
                            )
                        }
                    }
                }
            }
        }
    }

    private fun tampilkanSnackbarError(pesan: String, canRetry: Boolean) {
        val snackbar = Snackbar.make(coordinatorLayout, pesan, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_error))
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))

        if (canRetry) {
            snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.white))
            snackbar.setAction("COBA LAGI") {
                val barcode = etBarcode.text.toString().trim()
                eksekusiTransaksi(barcode)
            }
        }

        snackbar.show()
    }

    // TUGAS MANDIRI 1: Fallback ke Pembayaran Tunai Manual jika gagal retry 3x
    private fun tampilkanDialogFallbackPembayaran() {
        AlertDialog.Builder(this)
            .setTitle("Pembayaran Gagal")
            .setMessage("Pembayaran melalui jaringan perbankan gagal setelah 3 kali percobaan.\n\nSilakan alihkan pelanggan ke Pembayaran Tunai Manual.")
            .setPositiveButton("Pembayaran Tunai Manual") { dialog, _ ->
                retryAttempt = 0
                Toast.makeText(this, "Dialihkan ke Pembayaran Tunai Manual", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                retryAttempt = 0
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // TUGAS MANDIRI 2: Dialog Otorisasi Supervisor jika nominal transaksi > Rp 10.000.000
    private fun tampilkanDialogOtorisasiSupervisor(barcode: String, maxLimit: Double) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val etPin = EditText(this).apply {
            hint = "Masukkan PIN Supervisor"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        container.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle("OTORISASI SUPERVISOR")
            .setMessage("Transaksi melebihi batas otorisasi kasir (Maksimal Rp %,.0f).\n\nSilakan masukkan PIN Supervisor untuk menyetujui.".format(maxLimit))
            .setView(container)
            .setPositiveButton("OTORISASI") { dialog, _ ->
                val inputPin = etPin.text.toString().trim()

                // CATATAN: PIN Supervisor dummy ("1234") untuk kebutuhan praktikum
                // JANGAN menyimpan PIN secara plaintext pada database
                if (inputPin == DUMMY_SUPERVISOR_PIN) {
                    Timber.i("Otorisasi Supervisor Berhasil dengan PIN dummy")
                    Toast.makeText(this, "Otorisasi Supervisor Berhasil", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Lanjutkan transaksi setelah otorisasi berhasil
                    eksekusiTransaksi(barcode, isAuthorizedBySupervisor = true)
                } else {
                    Timber.w("Otorisasi Supervisor Gagal: PIN Supervisor salah")
                    Toast.makeText(this, "PIN Supervisor salah", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("BATAL") { dialog, _ ->
                Timber.w("Otorisasi Supervisor dibatalkan oleh pengguna")
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
