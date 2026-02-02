package kr.hhplus.be.server.point.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QLedger is a Querydsl query type for Ledger
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QLedger extends EntityPathBase<Ledger> {

    private static final long serialVersionUID = -957762744L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QLedger ledger = new QLedger("ledger");

    public final NumberPath<java.math.BigDecimal> amount = createNumber("amount", java.math.BigDecimal.class);

    public final StringPath chargeDate = createString("chargeDate");

    public final StringPath chargeTime = createString("chargeTime");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<LedgerType> type = createEnum("type", LedgerType.class);

    public final QWallet wallet;

    public QLedger(String variable) {
        this(Ledger.class, forVariable(variable), INITS);
    }

    public QLedger(Path<? extends Ledger> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QLedger(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QLedger(PathMetadata metadata, PathInits inits) {
        this(Ledger.class, metadata, inits);
    }

    public QLedger(Class<? extends Ledger> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.wallet = inits.isInitialized("wallet") ? new QWallet(forProperty("wallet"), inits.get("wallet")) : null;
    }

}

