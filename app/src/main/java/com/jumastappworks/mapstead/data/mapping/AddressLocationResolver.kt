package com.jumastappworks.mapstead.data.mapping

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AddressSearchResult {
    data class Success(
        val matches: List<AddressLocationMatch>
    ) : AddressSearchResult

    data object NoMatches : AddressSearchResult
    data object Unavailable : AddressSearchResult
    data object NetworkFailure : AddressSearchResult
    data object InvalidQuery : AddressSearchResult
    data object Error : AddressSearchResult
}

data class AddressLocationMatch(
    val displayAddress: String,
    val latitude: Double,
    val longitude: Double
)

interface AddressLocationResolver {
    suspend fun search(query: String): AddressSearchResult
}

@Singleton
class ProductionAddressLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : AddressLocationResolver {

    override suspend fun search(query: String): AddressSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext AddressSearchResult.InvalidQuery
        
        if (!Geocoder.isPresent()) return@withContext AddressSearchResult.Unavailable
        
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(query, 5)
            
            if (results.isNullOrEmpty()) {
                AddressSearchResult.NoMatches
            } else {
                val matches = results.mapNotNull { address ->
                    val line = (0..address.maxAddressLineIndex).joinToString(", ") { address.getAddressLine(it) }
                    if (address.hasLatitude() && address.hasLongitude()) {
                        AddressLocationMatch(
                            displayAddress = line,
                            latitude = address.latitude,
                            longitude = address.longitude
                        )
                    } else null
                }
                if (matches.isEmpty()) AddressSearchResult.NoMatches
                else AddressSearchResult.Success(matches)
            }
        } catch (e: java.io.IOException) {
            AddressSearchResult.NetworkFailure
        } catch (e: Exception) {
            AddressSearchResult.Error
        }
    }
}
