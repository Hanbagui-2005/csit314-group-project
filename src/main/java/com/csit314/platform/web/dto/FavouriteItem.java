package com.csit314.platform.web.dto;

// mock favourites, real version comes from the favourites service later
public record FavouriteItem(Long id, String title, String designerName, String imageUrl) {
}
