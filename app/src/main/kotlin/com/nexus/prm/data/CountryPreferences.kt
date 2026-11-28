package com.nexus.prm.data

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CountryOption(
  val isoCode: String,
  val name: String,
  val dialCode: String,
) {
  val displayLabel: String get() = "$name (+$dialCode)"
}

object CountryPreferences {
  private const val PREFS_NAME = "nexus_country_prefs"
  private const val KEY_COUNTRY_ISO = "default_country_iso"
  private const val VALUE_NONE = "NONE"

  val countries: List<CountryOption> = listOf(
    CountryOption("AF", "Afghanistan", "93"),
    CountryOption("AR", "Argentina", "54"),
    CountryOption("AU", "Australia", "61"),
    CountryOption("AT", "Austria", "43"),
    CountryOption("BH", "Bahrain", "973"),
    CountryOption("BD", "Bangladesh", "880"),
    CountryOption("BE", "Belgium", "32"),
    CountryOption("BT", "Bhutan", "975"),
    CountryOption("BR", "Brazil", "55"),
    CountryOption("CA", "Canada", "1"),
    CountryOption("CL", "Chile", "56"),
    CountryOption("CN", "China", "86"),
    CountryOption("CO", "Colombia", "57"),
    CountryOption("DK", "Denmark", "45"),
    CountryOption("EG", "Egypt", "20"),
    CountryOption("FI", "Finland", "358"),
    CountryOption("FR", "France", "33"),
    CountryOption("DE", "Germany", "49"),
    CountryOption("GR", "Greece", "30"),
    CountryOption("HK", "Hong Kong", "852"),
    CountryOption("IN", "India", "91"),
    CountryOption("ID", "Indonesia", "62"),
    CountryOption("IE", "Ireland", "353"),
    CountryOption("IL", "Israel", "972"),
    CountryOption("IT", "Italy", "39"),
    CountryOption("JP", "Japan", "81"),
    CountryOption("KE", "Kenya", "254"),
    CountryOption("KW", "Kuwait", "965"),
    CountryOption("MY", "Malaysia", "60"),
    CountryOption("MV", "Maldives", "960"),
    CountryOption("MU", "Mauritius", "230"),
    CountryOption("MX", "Mexico", "52"),
    CountryOption("MM", "Myanmar", "95"),
    CountryOption("NP", "Nepal", "977"),
    CountryOption("NL", "Netherlands", "31"),
    CountryOption("NZ", "New Zealand", "64"),
    CountryOption("NG", "Nigeria", "234"),
    CountryOption("NO", "Norway", "47"),
    CountryOption("OM", "Oman", "968"),
    CountryOption("PK", "Pakistan", "92"),
    CountryOption("PH", "Philippines", "63"),
    CountryOption("PL", "Poland", "48"),
    CountryOption("PT", "Portugal", "351"),
    CountryOption("QA", "Qatar", "974"),
    CountryOption("RU", "Russia", "7"),
    CountryOption("SA", "Saudi Arabia", "966"),
    CountryOption("SG", "Singapore", "65"),
    CountryOption("ZA", "South Africa", "27"),
    CountryOption("KR", "South Korea", "82"),
    CountryOption("ES", "Spain", "34"),
    CountryOption("LK", "Sri Lanka", "94"),
    CountryOption("SE", "Sweden", "46"),
    CountryOption("CH", "Switzerland", "41"),
    CountryOption("TW", "Taiwan", "886"),
    CountryOption("TH", "Thailand", "66"),
    CountryOption("TR", "Turkey", "90"),
    CountryOption("AE", "United Arab Emirates", "971"),
    CountryOption("GB", "United Kingdom", "44"),
    CountryOption("US", "United States", "1"),
    CountryOption("VN", "Vietnam", "84"),
  )

  private val sharedCountry = MutableStateFlow<CountryOption?>(null)
  private var initialized = false

  fun findByIso(isoCode: String?): CountryOption? {
    if (isoCode.isNullOrBlank()) return null
    return countries.firstOrNull { it.isoCode.equals(isoCode.trim(), ignoreCase = true) }
  }

  fun resolveSavedCountry(rawIso: String?, deviceCountryIso: String = Locale.getDefault().country): CountryOption? {
    if (rawIso == VALUE_NONE) return null
    if (!rawIso.isNullOrBlank()) return findByIso(rawIso)
    return findByIso(deviceCountryIso)
  }

  fun countryFlow(context: Context): StateFlow<CountryOption?> {
    if (!initialized) {
      val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      sharedCountry.value = resolveSavedCountry(prefs.getString(KEY_COUNTRY_ISO, null))
      initialized = true
    }
    return sharedCountry.asStateFlow()
  }

  fun setCountry(context: Context, option: CountryOption?) {
    val appContext = context.applicationContext
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_COUNTRY_ISO, option?.isoCode ?: VALUE_NONE)
      .apply()
    sharedCountry.value = option
  }
}
