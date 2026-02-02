package kr.hhplus.be.server.reservation.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReservation is a Querydsl query type for Reservation
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReservation extends EntityPathBase<Reservation> {

    private static final long serialVersionUID = 531180497L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QReservation reservation = new QReservation("reservation");

    public final NumberPath<java.math.BigDecimal> amountCents = createNumber("amountCents", java.math.BigDecimal.class);

    public final kr.hhplus.be.server.concert.domain.QConcertSchedule concertSchedule;

    public final DateTimePath<java.time.LocalDateTime> holdExpiresAt = createDateTime("holdExpiresAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final kr.hhplus.be.server.concert.domain.QSeat seat;

    public final EnumPath<ReservationStatus> status = createEnum("status", ReservationStatus.class);

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public QReservation(String variable) {
        this(Reservation.class, forVariable(variable), INITS);
    }

    public QReservation(Path<? extends Reservation> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QReservation(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QReservation(PathMetadata metadata, PathInits inits) {
        this(Reservation.class, metadata, inits);
    }

    public QReservation(Class<? extends Reservation> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.concertSchedule = inits.isInitialized("concertSchedule") ? new kr.hhplus.be.server.concert.domain.QConcertSchedule(forProperty("concertSchedule"), inits.get("concertSchedule")) : null;
        this.seat = inits.isInitialized("seat") ? new kr.hhplus.be.server.concert.domain.QSeat(forProperty("seat"), inits.get("seat")) : null;
    }

}

