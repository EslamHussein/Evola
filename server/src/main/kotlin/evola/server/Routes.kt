package evola.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}

fun Route.materialRoutes(materialService: MaterialService) {
    post("/api/materials") {
        val request = call.receive<UploadMaterialRequest>()
        val response = materialService.uploadMaterial(request)
        call.respond(HttpStatusCode.OK, response)
    }
}
