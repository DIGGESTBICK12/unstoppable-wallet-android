package io.horizontalsystems.bankwallet.core

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.squareup.leakcanary.LeakCanary
import io.horizontalsystems.bankwallet.BuildConfig
import io.horizontalsystems.bankwallet.core.factories.AccountFactory
import io.horizontalsystems.bankwallet.core.factories.AdapterFactory
import io.horizontalsystems.bankwallet.core.managers.*
import io.horizontalsystems.bankwallet.core.security.EncryptionManager
import io.horizontalsystems.bankwallet.core.storage.AccountsStorage
import io.horizontalsystems.bankwallet.core.storage.AppDatabase
import io.horizontalsystems.bankwallet.core.storage.EnabledWalletsStorage
import io.horizontalsystems.bankwallet.core.storage.RatesRepository
import io.horizontalsystems.bankwallet.modules.fulltransactioninfo.FullTransactionInfoFactory
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class App : Application() {

    companion object {

        lateinit var preferences: SharedPreferences

        lateinit var feeRateProvider: IFeeRateProvider
        lateinit var secureStorage: ISecuredStorage
        lateinit var localStorage: ILocalStorage
        lateinit var encryptionManager: EncryptionManager
        lateinit var wordsManager: WordsManager
        lateinit var authManager: AuthManager
        lateinit var randomManager: IRandomProvider
        lateinit var networkManager: INetworkManager
        lateinit var currencyManager: ICurrencyManager
        lateinit var backgroundManager: BackgroundManager
        lateinit var languageManager: ILanguageManager
        lateinit var systemInfoManager: ISystemInfoManager
        lateinit var pinManager: IPinManager
        lateinit var lockManager: ILockManager
        lateinit var appConfigProvider: IAppConfigProvider
        lateinit var adapterManager: IAdapterManager
        lateinit var walletManager: IWalletManager
        lateinit var walletFactory: IWalletFactory
        lateinit var walletStorage: IWalletStorage
        lateinit var accountManager: IAccountManager
        lateinit var backupManager: IBackupManager
        lateinit var accountCreator: IAccountCreator
        lateinit var predefinedAccountTypeManager: IPredefinedAccountTypeManager
        lateinit var defaultWalletCreator: DefaultWalletCreator
        lateinit var walletRemover: WalletRemover

        lateinit var rateSyncer: RateSyncer
        lateinit var rateManager: RateManager
        lateinit var networkAvailabilityManager: NetworkAvailabilityManager
        lateinit var appDatabase: AppDatabase
        lateinit var rateStorage: IRateStorage
        lateinit var accountsStorage: IAccountsStorage
        lateinit var enabledWalletsStorage: IEnabledWalletStorage
        lateinit var transactionInfoFactory: FullTransactionInfoFactory
        lateinit var transactionDataProviderManager: TransactionDataProviderManager
        lateinit var appCloseManager: AppCloseManager
        lateinit var ethereumKitManager: IEthereumKitManager
        lateinit var eosKitManager: IEosKitManager
        lateinit var numberFormatter: IAppNumberFormatter
        lateinit var appManager: ILaunchManager

        lateinit var instance: App
            private set

        var lastExitDate: Long = 0
    }

    override fun onCreate() {
        super.onCreate()

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        LeakCanary.install(this)

        if (!BuildConfig.DEBUG) {
            //Disable logging for lower levels in Release build
            Logger.getLogger("").level = Level.SEVERE
        }

        instance = this
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val fallbackLanguage = Locale("en")

        appConfigProvider = AppConfigProvider()
        feeRateProvider = FeeRateProvider(instance, appConfigProvider)
        backgroundManager = BackgroundManager(this)
        encryptionManager = EncryptionManager
        secureStorage = SecuredStorageManager(encryptionManager)
        ethereumKitManager = EthereumKitManager(appConfigProvider)
        eosKitManager = EosKitManager(appConfigProvider)

        appDatabase = AppDatabase.getInstance(this)
        rateStorage = RatesRepository(appDatabase)
        accountsStorage = AccountsStorage(appDatabase)

        walletFactory = WalletFactory()
        enabledWalletsStorage = EnabledWalletsStorage(appDatabase)
        walletStorage = WalletStorage(appConfigProvider, walletFactory, enabledWalletsStorage)
        localStorage = LocalStorageManager()

        wordsManager = WordsManager(localStorage)
        networkManager = NetworkManager(appConfigProvider)
        rateManager = RateManager(rateStorage, networkManager)
        accountManager = AccountManager(accountsStorage)
        backupManager = BackupManager(accountManager)
        walletManager = WalletManager(accountManager, walletFactory, walletStorage)
        defaultWalletCreator = DefaultWalletCreator(walletManager, appConfigProvider, walletFactory)
        accountCreator = AccountCreator(accountManager, AccountFactory(), wordsManager, defaultWalletCreator)
        predefinedAccountTypeManager = PredefinedAccountTypeManager(appConfigProvider, accountManager, accountCreator)
        authManager = AuthManager(secureStorage, localStorage, walletManager, rateManager, ethereumKitManager, appConfigProvider)
        walletRemover = WalletRemover(accountManager, walletManager)

        randomManager = RandomProvider()
        systemInfoManager = SystemInfoManager()
        pinManager = PinManager(secureStorage)
        lockManager = LockManager(secureStorage)
        languageManager = LanguageManager(localStorage, appConfigProvider, fallbackLanguage)
        currencyManager = CurrencyManager(localStorage, appConfigProvider)
        numberFormatter = NumberFormatter(languageManager)

        networkAvailabilityManager = NetworkAvailabilityManager()

        adapterManager = AdapterManager(walletManager, AdapterFactory(instance, appConfigProvider, ethereumKitManager, eosKitManager, feeRateProvider), ethereumKitManager, eosKitManager)
        rateSyncer = RateSyncer(rateManager, adapterManager, currencyManager, networkAvailabilityManager)

        appCloseManager = AppCloseManager()

        transactionDataProviderManager = TransactionDataProviderManager(appConfigProvider, localStorage)
        transactionInfoFactory = FullTransactionInfoFactory(networkManager, transactionDataProviderManager)

        authManager.adapterManager = adapterManager
        authManager.pinManager = pinManager

        appManager = AppManager(accountManager, walletManager)
        appManager.onStart()
    }

}
