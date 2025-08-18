package fr.prisontycoon.pets;

/**
 * Données d'un pet possédé par un joueur.
 */
public class OwnedPetData {
    public String id;       // id dans PetRegistry
    public int growth;      // 0..50
    public long xp;         // xp courante vers prochaine croissance
    public boolean equipped;
}


