package com.pos.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvenementPOS {

    public enum TypeEvenement {
        VENTE, RETOUR, OUVERTURE
    }

    private TypeEvenement type;
    private String idCaisse;
    private String ville;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant timestamp;

    private Double montant;
    private List<String> produits;

    public EvenementPOS() {}

    public EvenementPOS(TypeEvenement type, String idCaisse, String ville,
                        Instant timestamp, Double montant, List<String> produits) {
        this.type = type;
        this.idCaisse = idCaisse;
        this.ville = ville;
        this.timestamp = timestamp;
        this.montant = montant;
        this.produits = produits;
    }

    public TypeEvenement getType() { return type; }
    public void setType(TypeEvenement type) { this.type = type; }

    public String getIdCaisse() { return idCaisse; }
    public void setIdCaisse(String idCaisse) { this.idCaisse = idCaisse; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getMontant() { return montant; }
    public void setMontant(Double montant) { this.montant = montant; }

    public List<String> getProduits() { return produits; }
    public void setProduits(List<String> produits) { this.produits = produits; }

    @Override
    public String toString() {
        return String.format("[%s] %s | Caisse: %s | Ville: %s | Montant: %.2f DT",
                type, timestamp, idCaisse, ville, montant != null ? montant : 0.0);
    }
}
