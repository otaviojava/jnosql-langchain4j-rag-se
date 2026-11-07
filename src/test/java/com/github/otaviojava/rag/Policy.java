package com.github.otaviojava.rag;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import org.eclipse.jnosql.mapping.vector.DenseVector;

@Entity("policy_integration_test")
public class Policy {

    @Id
    private String id;

    @Column
    private String title;

    @Column
    private String content;

    @Column
    private DenseVector vector;

    public Policy() {
    }

    public Policy(String id, String title, String content, DenseVector vector) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.vector = vector;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public DenseVector getVector() {
        return vector;
    }
}
