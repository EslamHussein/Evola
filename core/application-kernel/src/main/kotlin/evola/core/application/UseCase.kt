package evola.core.application

interface Command<out R>

interface Query<out R>

fun interface UseCase<C, R> {
    suspend fun handle(input: C): R
}
