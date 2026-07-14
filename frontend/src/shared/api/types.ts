/** Forma de una Page de Spring Data devuelta por los endpoints paginados. */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
