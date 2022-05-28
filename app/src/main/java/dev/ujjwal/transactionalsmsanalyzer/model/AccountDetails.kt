package dev.ujjwal.transactionalsmsanalyzer.model

data class AccountDetails(
    val accountNo: String,
    val balance: String,
    val date: Long,
    val formattedDate: String,
    val accountType: String,
    val bank: String

)