package bitcoin.wallet.modules.transactions

import bitcoin.wallet.core.DatabaseChangeset
import bitcoin.wallet.core.IDatabaseManager
import bitcoin.wallet.entities.coins.bitcoin.Bitcoin
import bitcoin.wallet.entities.CoinValue
import bitcoin.wallet.entities.TransactionRecord
import bitcoin.wallet.modules.RxBaseTest
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.*

class TransactionsInteractorTest {

    private val delegate = mock(TransactionsModule.IInteractorDelegate::class.java)
    private val databaseManager = mock(IDatabaseManager::class.java)

    private val interactor = TransactionsInteractor(databaseManager)

    @Before
    fun before() {
        RxBaseTest.setup()

        interactor.delegate = delegate
    }

    @Test
    fun retrieveTransactionRecords() {
        whenever(databaseManager.getTransactionRecords()).thenReturn(Observable.empty())

        interactor.retrieveTransactionRecords()

        verify(databaseManager).getTransactionRecords()
    }

    @Test
    fun retrieveTransactionItems_success() {
        val transactionRecords = listOf<TransactionRecord>()

        whenever(databaseManager.getTransactionRecords()).thenReturn(Observable.just(DatabaseChangeset(transactionRecords)))

        interactor.retrieveTransactionRecords()

        verify(delegate).didRetrieveTransactionRecords(listOf())
    }

    @Test
    fun retrieveTransactionItems_transactionOutConvert() {
        val now = Date()

        val transactionRecord = TransactionRecord().apply {
            hash = "hash"
            amount = 100000000
            fee = 1000000
            incoming = true
            timestamp = now.time
            from = "from-address"
            to = "to-address"
            blockHeight = 123
        }


        whenever(databaseManager.getTransactionRecords()).thenReturn(Observable.just(DatabaseChangeset(listOf(transactionRecord))))

        interactor.retrieveTransactionRecords()

        val expectedItems = listOf(TransactionRecordViewItem(
                "hash",
                CoinValue(Bitcoin(), 1.0),
                CoinValue(Bitcoin(), 0.01),
                "from-address",
                "to-address",
                true,
                123,
                now,
                null,
                null
        ))

        verify(delegate).didRetrieveTransactionRecords(expectedItems)
    }

}
