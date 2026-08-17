package com.xindong.common.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;

import java.sql.Types;

public class XindongSQLiteDialect extends SQLiteDialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        var ddl = typeContributions.getTypeConfiguration().getDdlTypeRegistry();
        ddl.addDescriptor(Types.BIGINT,    new DdlTypeImpl(Types.BIGINT,    "INTEGER", this));
        ddl.addDescriptor(Types.TINYINT,   new DdlTypeImpl(Types.TINYINT,   "INTEGER", this));
        ddl.addDescriptor(Types.SMALLINT,  new DdlTypeImpl(Types.SMALLINT,  "INTEGER", this));
        ddl.addDescriptor(Types.DATE,      new DdlTypeImpl(Types.DATE,      "TEXT",    this));
        ddl.addDescriptor(Types.TIME,      new DdlTypeImpl(Types.TIME,      "TEXT",    this));
        ddl.addDescriptor(Types.TIMESTAMP, new DdlTypeImpl(Types.TIMESTAMP, "TEXT",    this));
    }
}