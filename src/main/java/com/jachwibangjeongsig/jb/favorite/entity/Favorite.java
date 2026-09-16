package com.jachwibangjeongsig.jb.favorite.entity;
import com.jachwibangjeongsig.jb.property.entity.Property;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "favorite", uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_property", columnNames = {"user_id", "property_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false) private Property property;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public Favorite(UUID userId, Property property) {
        this.userId = userId;
        this.property = property;
    }
}
