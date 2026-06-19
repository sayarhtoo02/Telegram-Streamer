package com.example.media

import androidx.media3.datasource.DataSource
import com.example.tdlib.TdLibClient

class TdLibDataSourceFactory(
    private val tdLibClient: TdLibClient
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TdLibDataSource(tdLibClient)
    }
}
