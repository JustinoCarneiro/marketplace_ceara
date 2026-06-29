package com.onda.marketplace.category;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Categoria do catálogo de serviços (US28). Mapeia a tabela
 * {@code service_categories} (criada na migration V1).
 */
@Entity
@Table(name = "service_categories")
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false)
    private boolean ativa = true;

    protected ServiceCategory() {}

    public ServiceCategory(String nome, String slug) {
        this.nome = nome;
        this.slug = slug;
    }

    public void renomear(String nome) { this.nome = nome; }
    public void ativar()    { this.ativa = true; }
    public void desativar() { this.ativa = false; }

    public UUID    getId()   { return id;   }
    public String  getNome() { return nome; }
    public String  getSlug() { return slug; }
    public boolean isAtiva() { return ativa; }
}
