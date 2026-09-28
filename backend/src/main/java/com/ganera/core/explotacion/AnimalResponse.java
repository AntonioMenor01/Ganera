package com.ganera.core.explotacion;

public record AnimalResponse(Long id, String crotal, String crotalUltimosDigitos) {

    public static AnimalResponse from(Animal animal) {
        return new AnimalResponse(animal.getId(), animal.getCrotal(), animal.getCrotalUltimosDigitos());
    }
}
