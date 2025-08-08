package com.example.groviq.backEnd.dataStructures

import com.example.groviq.backEnd.searchEngine.ArtistDto


data class audioSource(

    var nameOfAudioSource   : String = "",
    var artistOfAudioSource : ArtistDto = ArtistDto("", "", emptyList(), "", emptyList()),
    var yearOfAudioSource   : String = "",


    var songIds: MutableList<String> = mutableListOf()
)
