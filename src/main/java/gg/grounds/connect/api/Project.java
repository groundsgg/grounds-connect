package gg.grounds.connect.api;

/** A Grounds platform project the user can select. */
public record Project(String id, String slug, String name, String role) {

  public String displayName() {
    if (name != null && !name.isBlank()) {
      return name;
    }
    if (slug != null && !slug.isBlank()) {
      return slug;
    }
    return id;
  }
}
