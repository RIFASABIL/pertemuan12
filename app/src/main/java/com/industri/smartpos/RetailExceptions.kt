package com.industri.smartpos

import java.io.IOException

// 1. Exception ketika Barcode Produk tidak ditemukan di database katalog
class ProductBarcodeNotFoundException(
    val barcode: String
) : Exception(
    "Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem."
)

// 2. Exception ketika terjadi Timeout pada Gateway Pembayaran Kasir
class PaymentGatewayTimeoutException(
    val gatewayName: String
) : IOException(
    "Layanan pembayaran $gatewayName tidak merespons dalam 15 detik."
)

// 3. Exception ketika Nominal Transaksi melebihi Limit Otorisasi Kasir
class CashierLimitExceededException(
    val maxLimit: Double
) : Exception(
    "Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp %,.0f)"
        .format(maxLimit)
)
