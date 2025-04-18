package ee.schimke.shokz.metro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras

interface ViewModelCreator {
    fun create(extras: CreationExtras): ViewModel
}