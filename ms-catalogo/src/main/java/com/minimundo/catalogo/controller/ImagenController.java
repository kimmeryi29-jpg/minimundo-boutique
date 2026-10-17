package com.minimundo.catalogo.controller;

import com.minimundo.catalogo.service.ImagenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/imagenes")
@Tag(name = "Imágenes", description = "Imágenes de producto guardadas en MongoDB (GridFS)")
public class ImagenController {

    private final ImagenService imagenes;

    public ImagenController(ImagenService imagenes) {
        this.imagenes = imagenes;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Descargar una imagen de producto")
    public ResponseEntity<InputStreamResource> obtener(@PathVariable String id) throws IOException {
        GridFsResource r = imagenes.obtener(id);
        String tipo = r.getContentType() == null ? MediaType.IMAGE_JPEG_VALUE : r.getContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .contentLength(r.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new InputStreamResource(r.getInputStream()));
    }
}
