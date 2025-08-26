package com.example.groviq.backEnd.dataStructures

import com.example.groviq.backEnd.searchEngine.ArtistDto


data class audioSource(

    var nameOfAudioSource     : String = "",
    var artistsOfAudioSource  : List<ArtistDto> = emptyList(),
    var yearOfAudioSource     : String = "",
    var songIds: MutableList<String> = mutableListOf(),

    //params that make audiosource be saved by user
    var shouldBeSavedStrictly : Boolean = false
)
