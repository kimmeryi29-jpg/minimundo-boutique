package com.minimundo.catalogo.service;

import com.minimundo.catalogo.error.ApiException;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * Imágenes de producto guardadas en GridFS (MongoDB Atlas). Render no tiene disco persistente en el plan
 * gratuito, por eso las imágenes no se guardan en el sistema de archivos (HU-18).
 */
@Service
public class ImagenService {

    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");

    private final GridFsTemplate gridFs;
    private final long maxBytes;

    public ImagenService(GridFsTemplate gridFs, @Value("${minimundo.imagenes.max-kb:2048}") long maxKb) {
        this.gridFs = gridFs;
        this.maxBytes = maxKb * 1024;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    /** HU-18: solo JPG o PNG y hasta el tamaño máximo configurado. */
    public void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.badRequest("Selecciona al menos una imagen");
        }
        String tipo = tipoDe(archivo.getContentType(), archivo.getOriginalFilename());
        if (!TIPOS.contains(tipo)) {
            throw ApiException.badRequest("Formato no permitido en \"" + archivo.getOriginalFilename()
                    + "\". Solo se aceptan imágenes JPG o PNG.");
        }
        if (archivo.getSize() > maxBytes) {
            throw ApiException.badRequest("La imagen \"" + archivo.getOriginalFilename() + "\" supera el tamaño "
                    + "máximo de " + (maxBytes / 1024) + " KB.");
        }
    }

    public String guardar(MultipartFile archivo, String productoId) {
        validar(archivo);
        try (InputStream in = archivo.getInputStream()) {
            return guardar(in, archivo.getOriginalFilename(),
                    tipoDe(archivo.getContentType(), archivo.getOriginalFilename()), productoId);
        } catch (IOException e) {
            throw ApiException.badRequest("No fue posible leer la imagen");
        }
    }

    public String guardar(byte[] datos, String nombre, String contentType, String productoId) {
        return guardar(new ByteArrayInputStream(datos), nombre, contentType, productoId);
    }

    private String guardar(InputStream in, String nombre, String contentType, String productoId) {
        Document meta = new Document("productoId", productoId);
        ObjectId id = gridFs.store(in, nombre, contentType, meta);
        return id.toHexString();
    }

    public GridFsResource obtener(String id) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.notFound("La imagen no existe");
        }
        GridFSFile file = gridFs.findOne(new Query(Criteria.where("_id").is(new ObjectId(id))));
        if (file == null) {
            throw ApiException.notFound("La imagen no existe");
        }
        return gridFs.getResource(file);
    }

    public void eliminar(String id) {
        if (ObjectId.isValid(id)) {
            gridFs.delete(new Query(Criteria.where("_id").is(new ObjectId(id))));
        }
    }

    public static String tipoDe(String contentType, String nombre) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.equals("image/jpg") || ct.equals("image/pjpeg")) {
            return "image/jpeg";
        }
        if (TIPOS.contains(ct)) {
            return ct;
        }
        String n = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        return ct;
    }
}
