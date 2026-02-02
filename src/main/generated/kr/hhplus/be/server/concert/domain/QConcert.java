package kr.hhplus.be.server.concert.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QConcert is a Querydsl query type for Concert
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QConcert extends EntityPathBase<Concert> {

    private static final long serialVersionUID = 1419437457L;

    public static final QConcert concert = new QConcert("concert");

    public final StringPath concertDec = createString("concertDec");

    public final StringPath concertName = createString("concertName");

    public final EnumPath<kr.hhplus.be.server.concert.common.ConcertStatus> concertStatus = createEnum("concertStatus", kr.hhplus.be.server.concert.common.ConcertStatus.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public QConcert(String variable) {
        super(Concert.class, forVariable(variable));
    }

    public QConcert(Path<? extends Concert> path) {
        super(path.getType(), path.getMetadata());
    }

    public QConcert(PathMetadata metadata) {
        super(Concert.class, metadata);
    }

}

