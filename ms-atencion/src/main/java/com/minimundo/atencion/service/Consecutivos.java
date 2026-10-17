package com.minimundo.atencion.service;

import com.minimundo.atencion.model.Contador;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Números de radicado consecutivos y únicos (PQRS-000001, SOL-000001). */
@Component
public class Consecutivos {

    private final MongoTemplate mongo;

    public Consecutivos(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public String siguiente(String prefijo) {
        Contador c = mongo.findAndModify(new Query(Criteria.where("_id").is(prefijo)), new Update().inc("valor", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true), Contador.class);
        return String.format("%s-%06d", prefijo, c == null ? 1 : c.getValor());
    }
}
