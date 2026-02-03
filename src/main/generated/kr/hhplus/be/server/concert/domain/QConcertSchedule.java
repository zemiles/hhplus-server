package kr.hhplus.be.server.concert.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QConcertSchedule is a Querydsl query type for ConcertSchedule
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QConcertSchedule extends EntityPathBase<ConcertSchedule> {

    private static final long serialVersionUID = 652852040L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QConcertSchedule concertSchedule = new QConcertSchedule("concertSchedule");

    public final QConcert concert;

    public final StringPath concertDate = createString("concertDate");

    public final NumberPath<java.math.BigDecimal> concertPrice = createNumber("concertPrice", java.math.BigDecimal.class);

    public final NumberPath<Long> concertScheduleId = createNumber("concertScheduleId", Long.class);

    public final StringPath concertTime = createString("concertTime");

    public QConcertSchedule(String variable) {
        this(ConcertSchedule.class, forVariable(variable), INITS);
    }

    public QConcertSchedule(Path<? extends ConcertSchedule> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QConcertSchedule(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QConcertSchedule(PathMetadata metadata, PathInits inits) {
        this(ConcertSchedule.class, metadata, inits);
    }

    public QConcertSchedule(Class<? extends ConcertSchedule> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.concert = inits.isInitialized("concert") ? new QConcert(forProperty("concert")) : null;
    }

}

