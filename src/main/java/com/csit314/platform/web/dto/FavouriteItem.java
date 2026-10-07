package com.csit314.platform.web.dto;

/**
 * Placeholder view model for an entry in a customer's favourites list.
 * Temporary mock data until Role 3 wires this to a real favourites service.
 */
public record FavouriteItem(Long id, String title, String designerName, String imageUrl) {
}
