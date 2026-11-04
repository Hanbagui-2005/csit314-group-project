package com.csit314.platform.dto;

// PM-10 - a single project ranked by its own views + shortlists, so PM can see which
// individual projects might be worth featuring.
//
// Anthony's review - these are int here (not long like TopDesigner's) on purpose:
// Project.viewCount/shortlistCount are plain int fields, and the JPQL constructor
// expression that builds this passes them straight through. TopDesigner's fields are
// long because those come from a sum() aggregate, which JPQL always returns as Long -
// a genuinely different source type, not just a style choice
public record TopProject(Long projectId, String title, String companyName, int viewCount, int shortlistCount) {
}
